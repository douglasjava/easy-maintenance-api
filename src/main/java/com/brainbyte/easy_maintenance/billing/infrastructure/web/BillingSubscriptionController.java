package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing/subscription")
@RequiredArgsConstructor
@Tag(name = "Billing Subscription", description = "Operações de assinatura financeira")
public class BillingSubscriptionController {

    private final BillingSubscriptionService service;
    private final AuthenticationService authenticationService;

    @GetMapping
    @Operation(summary = "Retorna a assinatura financeira do usuário autenticado")
    public BillingSubscriptionResponse getSubscription() {
        Long userId = authenticationService.getCurrentUser().getId();
        return service.findByUser(userId)
                .map(IBillingMapper.INSTANCE::toBillingSubscriptionResponse)
                .orElseThrow(() -> new NotFoundException("Não existe assinatura para o usuário"));
    }
}
