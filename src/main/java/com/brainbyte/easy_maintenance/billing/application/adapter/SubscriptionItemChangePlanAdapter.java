package com.brainbyte.easy_maintenance.billing.application.adapter;

import com.brainbyte.easy_maintenance.billing.application.dto.request.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.request.SubscriptionItemChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemChangePlanResponse;
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
    private final BillingSubscriptionItemRepository itemRepository;

    @Transactional
    public SubscriptionItemChangePlanResponse changePlan(Long id, User user, SubscriptionItemChangePlanRequest request) {

        log.info("Inicio de fluxo para alteração de plano {} usuário {} - request: {}", id, user, request);

        BillingSubscriptionItem item = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item de assinatura não encontrado: " + id));

        if (!item.getBillingSubscription().getBillingAccount().getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Item de assinatura não encontrado.");
        }

        // EPIC-014/TASK-112: plano único por conta — organizações não têm mais plano próprio.
        if (item.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION) {
            throw new NotFoundException(
                    "Troca de plano por organização não é mais suportada. O plano é gerenciado no nível da conta (item USER).");
        }

        ChangePlanRequest serviceRequest = new ChangePlanRequest(request.newPlanCode(), request.applyImmediately());
        ChangePlanResponse serviceResponse = userPlanChangeService.changePlan(user.getId(), item.getId(), serviceRequest);

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
