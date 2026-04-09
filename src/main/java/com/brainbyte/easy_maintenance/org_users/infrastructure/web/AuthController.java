package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.helper.HttpUtils;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.LoginRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.LoginResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.PasswordResetService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.shared.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/auth")
@Tag(name = "Autenticação", description = "Endpoints para login e gerenciamento de senha")
public class AuthController {

    private final UsersService usersService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @RateLimit("login")
    @Operation(summary = "Realiza a autenticação do usuário")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse loginResponse = usersService.authenticate(request);

        boolean remember = Boolean.TRUE.equals(request.remember());
        ResponseCookie cookie = ResponseCookie.from("accessToken", loginResponse.accessToken())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(remember ? Duration.ofDays(30) : Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return loginResponse;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra a sessão do usuário")
    public void logout(HttpServletResponse response) {
        ResponseCookie clearCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Altera a senha do usuário (Primeiro acesso)")
    public void changePassword(@Valid @RequestBody UserDTO.ChangePasswordRequest request) {
        usersService.changePassword(request);
    }

    @PostMapping("/forgot-password")
    @RateLimit("forgot-password")
    @Operation(summary = "Inicia o fluxo de recuperação de senha")
    public UserDTO.AuthMessageResponse forgotPassword(@Valid @RequestBody UserDTO.ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        var clientIp = HttpUtils.getClientIp(httpRequest);
        var userAgent = HttpUtils.getUserAgent(httpRequest);

        passwordResetService.forgotPassword(request, clientIp, userAgent);
        return new UserDTO.AuthMessageResponse("If an account exists for this email, we have sent a password reset link.");
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redefine a senha do usuário utilizando o token")
    public void resetPassword(@Valid @RequestBody UserDTO.ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
    }

}
