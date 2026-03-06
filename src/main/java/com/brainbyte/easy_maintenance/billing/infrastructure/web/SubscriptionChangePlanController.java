package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.OrganizationPlanChangeService;
import com.brainbyte.easy_maintenance.billing.application.service.UserPlanChangeService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing/change-plan")
@RequiredArgsConstructor
@Tag(name = "Change Plan Subscriptions", description = "Alterar planos de assinatura do usuário / Organizações")
public class SubscriptionChangePlanController {

    private final OrganizationPlanChangeService organizationPlanChangeService;
    private final UserPlanChangeService userPlanChangeService;
    private final AuthenticationService authenticationService;

    @PostMapping("/user-subscription")
    @Operation(summary = "Realiza o upgrade ou downgrade do plano do usuário")
    public ChangePlanResponse changePlan(@Valid @RequestBody ChangePlanRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        return userPlanChangeService.changePlan(userId, request);
    }

    @PostMapping("/organization-subscription/{orgCode}")
    @Operation(summary = "Realiza o upgrade ou downgrade do plano da organização")
    public ChangePlanResponse changePlan(@PathVariable String orgCode, @Valid @RequestBody ChangePlanRequest request) {
        return organizationPlanChangeService.changePlan(orgCode, request);
    }

}
