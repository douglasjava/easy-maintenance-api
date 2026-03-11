package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.adapter.SubscriptionItemCancelAdapter;
import com.brainbyte.easy_maintenance.billing.application.adapter.SubscriptionItemChangePlanAdapter;
import com.brainbyte.easy_maintenance.billing.application.dto.SubscriptionItemCancelResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.SubscriptionItemChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.SubscriptionItemChangePlanResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/billing/subscription-items")
@Tag(name = "Subscription Items", description = "Gerenciamento de itens de assinatura")
public class SubscriptionItemController {
    
    private final SubscriptionItemChangePlanAdapter changePlanAdapter;
    private final SubscriptionItemCancelAdapter cancelAdapter;
    private final AuthenticationService authenticationService;

    @PostMapping("/{id}/change-plan")
    @Operation(summary = "Altera o plano de um item da assinatura")
    public SubscriptionItemChangePlanResponse changePlan(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionItemChangePlanRequest request) {

        var user = authenticationService.getCurrentUser();
        return changePlanAdapter.changePlan(id, user, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancela um item da assinatura")
    public SubscriptionItemCancelResponse cancel(@PathVariable Long id) {
        var user = authenticationService.getCurrentUser();
        return cancelAdapter.cancel(id, user);
    }
}
