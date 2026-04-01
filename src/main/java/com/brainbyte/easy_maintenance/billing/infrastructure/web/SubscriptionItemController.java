package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.adapter.SubscriptionItemCancelAdapter;
import com.brainbyte.easy_maintenance.billing.application.adapter.SubscriptionItemChangePlanAdapter;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemCancelResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.request.SubscriptionItemChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionItemService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/billing/subscription-items")
@Tag(name = "Subscription Items", description = "Gerenciamento de itens de assinatura")
public class SubscriptionItemController {
    
    private final SubscriptionItemChangePlanAdapter changePlanAdapter;
    private final SubscriptionItemCancelAdapter cancelAdapter;
    private final BillingSubscriptionItemService itemService;
    private final AuthenticationService authenticationService;

    @GetMapping("/{id}")
    @Operation(summary = "Obter detalhes de um item da assinatura")
    public BillingSubscriptionResponse.SubscriptionItemResponse findById(@PathVariable Long id) {
        return itemService.findById(id);
    }

    @PostMapping("/{id}/change-plan")
    @Operation(summary = "Alterar o plano de um item da assinatura")
    public SubscriptionItemChangePlanResponse changePlan(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionItemChangePlanRequest request) {

        var user = authenticationService.getCurrentUser();
        return changePlanAdapter.changePlan(id, user, request);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar um item da assinatura")
    public SubscriptionItemCancelResponse cancel(@PathVariable Long id) {
        var user = authenticationService.getCurrentUser();
        return cancelAdapter.cancel(id, user);
    }

    @PostMapping("/{id}/undo-cancel")
    @Operation(summary = "Desfazer o cancelamento de um item da assinatura")
    public SubscriptionItemCancelResponse undoCancel(@PathVariable Long id) {
        var user = authenticationService.getCurrentUser();
        return cancelAdapter.undoCancel(id, user);
    }

}
