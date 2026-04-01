package com.brainbyte.easy_maintenance.billing.application.adapter;

import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemCancelResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SubscriptionItemCancelAdapter {

    private final BillingSubscriptionService subscriptionService;
    private final BillingSubscriptionItemRepository itemRepository;

    @Transactional
    public SubscriptionItemCancelResponse cancel(Long id, User user) {

        BillingSubscriptionItem item = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item de assinatura não encontrado: " + id));

        if (!item.getBillingSubscription().getBillingAccount().getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Item de assinatura não encontrado.");
        }

        subscriptionService.scheduleItemCancellation(id);

        return SubscriptionItemCancelResponse.builder()
                .subscriptionItemId(id)
                .message("Cancelamento do item de assinatura agendado para o final do ciclo")
                .scheduled(true)
                .build();
    }

    @Transactional
    public SubscriptionItemCancelResponse undoCancel(Long id, User user) {

        BillingSubscriptionItem item = itemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Item de assinatura não encontrado: " + id));

        if (!item.getBillingSubscription().getBillingAccount().getUser().getId().equals(user.getId())) {
            throw new NotFoundException("Item de assinatura não encontrado.");
        }

        subscriptionService.undoItemCancellation(id);

        return SubscriptionItemCancelResponse.builder()
                .subscriptionItemId(id)
                .message("Cancelamento do item de assinatura desfeito")
                .scheduled(false)
                .build();
    }
}
