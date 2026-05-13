package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.helper.HttpUtils;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.LoginRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.LoginResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.PasswordResetService;
import com.brainbyte.easy_maintenance.org_users.application.service.TwoFactorService;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/auth")
@Tag(name = "Autenticação", description = "Endpoints para login e gerenciamento de senha")
public class AuthController {

    public static final String DOMAIN_NAME = "easymaintenance.com.br";
    public static final String ACCESS_TOKEN_COOKIE = "accessToken";

    private final UsersService usersService;
    private final PasswordResetService passwordResetService;
    private final TwoFactorService twoFactorService;

    @PostMapping("/login")
    @RateLimit("login")
    @Operation(summary = "Realiza a autenticação do usuário")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse loginResponse = usersService.authenticate(request);

        // Only set cookie when 2FA is NOT required (full token issued immediately)
        if (!loginResponse.requiresTwoFactor() && loginResponse.accessToken() != null) {
            boolean remember = Boolean.TRUE.equals(request.remember());
            ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, loginResponse.accessToken())
                    .httpOnly(true)
                    //.secure(true)
                    //.domain(DOMAIN_NAME)
                    .sameSite("Lax")
                    .path("/")
                    .maxAge(remember ? Duration.ofDays(30) : Duration.ofDays(7))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }

        return loginResponse;
    }

    @PostMapping("/2fa/verify")
    @RateLimit("login")
    @Operation(summary = "Verifica o código 2FA e emite o token completo de acesso")
    public LoginResponse verifyTwoFactor(
            @Valid @RequestBody UserDTO.TwoFactorVerifyRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = usersService.verifyTwoFactor(request);

        boolean remember = Boolean.TRUE.equals(request.remember());
        ResponseCookie cookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, loginResponse.accessToken())
                .httpOnly(true)
                .secure(true)
                .domain(DOMAIN_NAME)
                .sameSite("Lax")
                .path("/")
                .maxAge(remember ? Duration.ofDays(30) : Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return loginResponse;
    }

    @PostMapping("/2fa/request-recovery")
    @RateLimit("forgot-password")
    @Operation(summary = "Solicita recuperação de 2FA via e-mail")
    public UserDTO.AuthMessageResponse requestTwoFactorRecovery(
            @Valid @RequestBody UserDTO.TwoFactorRecoveryRequest request) {
        twoFactorService.requestEmailRecovery(request.email());
        return new UserDTO.AuthMessageResponse(
                "Se houver um 2FA ativo para este e-mail, enviaremos as instruções de recuperação.");
    }

    @PostMapping("/2fa/apply-recovery")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desabilita o 2FA usando o token de recuperação enviado por e-mail")
    public void applyTwoFactorRecovery(@Valid @RequestBody UserDTO.TwoFactorRecoveryApplyRequest request) {
        twoFactorService.applyEmailRecovery(request.email(), request.recoveryToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Encerra a sessão do usuário")
    public void logout(HttpServletResponse response) {
        ResponseCookie clearCookie = ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .domain(DOMAIN_NAME)
                .sameSite("Lax")
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
