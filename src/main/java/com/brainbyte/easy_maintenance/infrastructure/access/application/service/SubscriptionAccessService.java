package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {

    private final BillingSubscriptionItemRepository subscriptionItemRepository;

    @Transactional(readOnly = true)
    public AccessMode resolveUserAccessMode(Long userId) {
        List<BillingSubscriptionItem> items = subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.USER, List.of(userId.toString()));

        if (items.isEmpty()) {
            return AccessMode.READ_ONLY;
        }

        BillingSubscriptionItem item = items.stream().findFirst()
                .orElseThrow(() -> new NotFoundException(String.format("Dados de assinatura de usuário %s não encontrados", userId)));

        return mapToAccessMode(item.getBillingSubscription().getStatus());
    }

    @Transactional(readOnly = true)
    public AccessMode resolveOrganizationAccessMode(String organizationCode) {
        List<BillingSubscriptionItem> items = subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of(organizationCode));

        if (items.isEmpty()) {
            return AccessMode.READ_ONLY;
        }

        BillingSubscriptionItem item = items.stream().findFirst()
                .orElseThrow(() -> new NotFoundException(String.format("Dados de assinatura de organização %s não encontrados",  organizationCode)));

        return mapToAccessMode(item.getBillingSubscription().getStatus());
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionStatus> getUserSubscriptionStatus(Long userId) {
         return subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.USER, List.of(userId.toString()))
                 .stream().findFirst().map(item -> item.getBillingSubscription().getStatus());
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionStatus> getOrganizationSubscriptionStatus(String organizationCode) {
        return subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of(organizationCode))
                .stream().findFirst().map(item -> item.getBillingSubscription().getStatus());
    }

    private AccessMode mapToAccessMode(SubscriptionStatus status) {
        if (status == null) return AccessMode.READ_ONLY;
        
        return switch (status) {
            case ACTIVE, TRIAL -> AccessMode.FULL_ACCESS;
            case BLOCKED -> AccessMode.NO_ACCESS;
            case CANCELED, PAST_DUE, PENDING_PAYMENT, PAYMENT_FAILED, NONE, PENDING_ACTIVATION -> AccessMode.READ_ONLY;
        };
    }
}
