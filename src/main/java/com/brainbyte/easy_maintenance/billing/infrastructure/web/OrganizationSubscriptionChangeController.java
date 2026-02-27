package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.OrganizationPlanChangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing/organization-subscription")
@RequiredArgsConstructor
@Tag(name = "Organization Subscription", description = "Operações de assinatura de organização")
public class OrganizationSubscriptionChangeController {

    private final OrganizationPlanChangeService organizationPlanChangeService;

    @PostMapping("/{orgCode}/change-plan")
    @Operation(summary = "Realiza o upgrade ou downgrade do plano da organização")
    public ChangePlanResponse changePlan(@PathVariable String orgCode, @Valid @RequestBody ChangePlanRequest request) {
        return organizationPlanChangeService.changePlan(orgCode, request);
    }

}
