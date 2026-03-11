package com.brainbyte.easy_maintenance.billing.application.adapter;

import com.brainbyte.easy_maintenance.billing.application.dto.SubscriptionItemCancelResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
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

        if (item.getSourceType() == BillingSubscriptionItemSourceType.USER) {
            throw new RuleException("Não é possível cancelar o item principal da assinatura do usuário.");
        }

        subscriptionService.removeItem(id);

        return SubscriptionItemCancelResponse.builder()
                .subscriptionItemId(id)
                .message("Item de assinatura cancelado com sucesso")
                .build();
    }
}
