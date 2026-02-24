package com.brainbyte.easy_maintenance.onboarding.infrastructure.web;


import com.brainbyte.easy_maintenance.onboarding.application.dto.OnboardingDTO;
import com.brainbyte.easy_maintenance.onboarding.application.service.OnboardingService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/me/onboarding")
@Tag(name = "Onboarding Usuário", description = "Cadastro inicial de usuário e empresa")
public class OnboardingController {

    private final AuthenticationService authenticationService;
    private final OnboardingService onboardingService;

    @PostMapping("/user")
    @Operation(summary = "Criar dados de usuário inicial")
    public OnboardingDTO.AccountUserResponse createUser(@Valid @RequestBody OnboardingDTO.AccountUserRequest request) {
        var user = authenticationService.getCurrentUser();
        return onboardingService.createUser(user, request);
    }

    @PostMapping("/organization")
    @Operation(summary = "Criar dados de organização inicial")
    public OnboardingDTO.AccountOrganizationResponse createOrganization(@Valid @RequestBody OnboardingDTO.AccountOrganizationRequest request) {
        var user = authenticationService.getCurrentUser();
        return onboardingService.createOrganization(user, request);
    }


}
