package com.brainbyte.easy_maintenance.billing.application.adapter;

import com.brainbyte.easy_maintenance.billing.application.dto.request.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.request.SubscriptionItemChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.OrganizationPlanChangeService;
import com.brainbyte.easy_maintenance.billing.application.service.UserPlanChangeService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionItemChangePlanAdapter {

    private final UserPlanChangeService userPlanChangeService;
    private final OrganizationPlanChangeService organizationPlanChangeService;
    private final BillingSubscriptionItemRepository itemRepository;

    @Transactional
    public SubscriptionItemChangePlanResponse changePlan(Long id, User user, SubscriptionItemChangePlanRequest request) {

        log.info("Inicio de fluxo para alteração de plano {} usuário {} - request: {}", id, user, request);

        BillingSubscriptionItem item = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item de assinatura não encontrado: " + id));

        if (!item.getBillingSubscription().getBillingAccount().getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Item de assinatura não encontrado.");
        }

        ChangePlanRequest serviceRequest = new ChangePlanRequest(request.newPlanCode(), request.applyImmediately());
        ChangePlanResponse serviceResponse;

        if (item.getSourceType() == BillingSubscriptionItemSourceType.USER) {
            serviceResponse = userPlanChangeService.changePlan(user.getId(), item.getId(), serviceRequest);
        } else if (item.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION) {
            serviceResponse = organizationPlanChangeService.changePlan(item.getSourceId(), item.getId(), serviceRequest);
        } else {
            throw new IllegalArgumentException("Tipo de item de assinatura não suportado: " + item.getSourceType());
        }

        return SubscriptionItemChangePlanResponse.builder()
                .subscriptionItemId(item.getId())
                .currentPlan(item.getPlan().getCode())
                .newPlan(request.newPlanCode())
                .effectiveAt(serviceResponse.effectiveAt())
                .applyImmediately(request.applyImmediately())
                .message(serviceResponse.type() == ChangePlanResponse.PlanChangeType.UPGRADE ? 
                        "Plan changed successfully" : "Plan change scheduled for next billing cycle")
                .build();
    }
}
