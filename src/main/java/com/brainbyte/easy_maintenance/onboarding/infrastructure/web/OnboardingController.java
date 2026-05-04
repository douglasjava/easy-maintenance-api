package com.brainbyte.easy_maintenance.onboarding.infrastructure.web;


import com.brainbyte.easy_maintenance.onboarding.application.dto.OnboardingDTO;
import com.brainbyte.easy_maintenance.onboarding.application.service.OnboardingService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/me/onboarding")
@Tag(name = "Onboarding Usuário", description = "Cadastro inicial de usuário e empresa")
public class OnboardingController {

    private final AuthenticationService authenticationService;
    private final OnboardingService onboardingService;
    private final UsersService usersService;

    @PostMapping("/user")
    @Operation(summary = "Criar dados de usuário inicial")
    public OnboardingDTO.AccountUserResponse createUser(@Valid @RequestBody OnboardingDTO.AccountUserRequest request) {
        var user = authenticationService.getCurrentUser();
        return onboardingService.createUser(user, request);
    }

    @PostMapping("/organization")
    @Operation(summary = "Criar dados de organização inicial")
    public OnboardingDTO.AccountOrganizationResponse createOrganization(
            @Valid @RequestBody OnboardingDTO.AccountOrganizationRequest request,
            HttpServletResponse response) {
        var user = authenticationService.getCurrentUser();
        var result = onboardingService.createOrganization(user, request);

        // JWT emitido no login não continha orgs (usuário sem empresa ainda).
        // Após criar a organização, reemitir o cookie com JWT atualizado para que
        // TenantFilter.validateOrgMembership() passe na próxima chamada ao /me/access-context.
        String refreshedToken = usersService.issueRefreshedToken(user.getId());
        ResponseCookie cookie = ResponseCookie.from("accessToken", refreshedToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return result;
    }

}
