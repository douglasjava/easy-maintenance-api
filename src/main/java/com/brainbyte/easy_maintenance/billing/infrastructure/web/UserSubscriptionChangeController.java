package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.UserPlanChangeService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing/user-subscription")
@RequiredArgsConstructor
@Tag(name = "User Subscription", description = "Operações de assinatura do usuário")
public class UserSubscriptionChangeController {

    private final UserPlanChangeService userPlanChangeService;
    private final AuthenticationService authenticationService;

    @PostMapping("/change-plan")
    @Operation(summary = "Realiza o upgrade ou downgrade do plano do usuário")
    public ChangePlanResponse changePlan(@Valid @RequestBody ChangePlanRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        return userPlanChangeService.changePlan(userId, request);
    }

}
