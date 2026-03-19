package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSubscriptionService {

    private final BillingSubscriptionRepository repository;
    private final BillingSubscriptionItemRepository itemRepository;
    private final AsaasClient asaasClient;

    @Transactional
    public void applyPendingPlans(BillingSubscriptionItem billingSubscriptionItem) {
        log.info("Applying pending plans for billing subscription {}", billingSubscriptionItem.getId());

        var billingSubscription = billingSubscriptionItem.getBillingSubscription();

        boolean updated = false;
        if (billingSubscriptionItem.getNextPlan() != null) {
            billingSubscriptionItem.setPlan(billingSubscriptionItem.getNextPlan());
            billingSubscriptionItem.setValueCents(billingSubscriptionItem.getNextPlan().getPriceCents().longValue());
            itemRepository.save(billingSubscriptionItem);
            updated = true;
        }

        if (updated) {
            recalculateTotal(billingSubscription);
            updateAsaasSubscription(billingSubscription);
            repository.save(billingSubscription);
        }
    }


    @Transactional
    public void scheduleItemCancellation(Long itemId) {
        log.info("Scheduling cancellation for subscription item: {}", itemId);
        BillingSubscriptionItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item de assinatura não encontrado: " + itemId));

        item.setCancelAtPeriodEnd(true);
        itemRepository.save(item);

        BillingSubscription subscription = item.getBillingSubscription();
        recalculateTotal(subscription);
        updateAsaasSubscription(subscription);
        repository.save(subscription);

    }

    @Transactional
    public void processPendingCancellations() {
        log.info("Processing pending subscription item cancellations");
        var items = itemRepository.findPendingCancellations();
        log.info("Found {} items for effective cancellation", items.size());

        items.forEach(item -> {
            try {
                log.info("Applying effective cancellation for item {}", item.getId());
                item.setCanceledAt(Instant.now());
                item.setValueCents(0L);
                item.setCancelAtPeriodEnd(false);
                itemRepository.save(item);

                BillingSubscription subscription = item.getBillingSubscription();
                recalculateTotal(subscription);
                updateAsaasSubscription(subscription);
                repository.save(subscription);
            } catch (Exception e) {
                log.error("Failed to process cancellation for item {}: {}", item.getId(), e.getMessage());
            }
        });
    }

    @Transactional
    public void removeItem(Long itemId) {
        log.info("Removing item from subscription: {}", itemId);
        BillingSubscriptionItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item de assinatura não encontrado: " + itemId));
        
        BillingSubscription subscription = item.getBillingSubscription();
        itemRepository.delete(item);
        
        recalculateTotal(subscription);
        updateAsaasSubscription(subscription);
        repository.save(subscription);
    }

    private void recalculateTotal(BillingSubscription subscription) {
        Long newTotal = itemRepository.findAllByBillingSubscriptionId(subscription.getId())
                .stream()
                .filter(i -> !i.isCancelAtPeriodEnd())
                .mapToLong(BillingSubscriptionItem::getValueCents)
                .sum();
        subscription.setTotalCents(newTotal);
    }

    private void updateAsaasSubscription(BillingSubscription subscription) {
        if (subscription.getExternalSubscriptionId() == null) return;

        if (subscription.getTotalCents() == 0) {
            cancelAsaasSubscription(subscription);
            return;
        }

        var req = new AsaasDTO.UpdateSubscriptionRequest(
                BigDecimal.valueOf(subscription.getTotalCents(), 2),
                null,
                null,
                null
        );

        try {
            asaasClient.updateSubscription(subscription.getExternalSubscriptionId(), req);
            log.info("Asaas subscription {} updated to new value {}", subscription.getExternalSubscriptionId(), req.value());
        } catch (Exception e) {
            log.error("Failed to update Asaas subscription {}: {}", subscription.getExternalSubscriptionId(), e.getMessage());
        }
    }

    private void cancelAsaasSubscription(BillingSubscription subscription) {
        try {
            asaasClient.cancelSubscription(subscription.getExternalSubscriptionId());
            subscription.setStatus(SubscriptionStatus.CANCELED);
            log.info("Asaas subscription {} canceled as total value is zero", subscription.getExternalSubscriptionId());
        } catch (Exception e) {
            log.error("Failed to cancel Asaas subscription {}: {}", subscription.getExternalSubscriptionId(), e.getMessage());
        }
    }

    @Transactional
    public BillingSubscription createTrial(BillingAccount billingAccount, Duration trialDuration) {
        log.info("Creating trial subscription for account {}", billingAccount.getId());
        
        if (repository.findByBillingAccountUserId(billingAccount.getUser().getId()).isPresent()) {
            throw new IllegalStateException("Billing subscription already exists for user " + billingAccount.getUser().getId());
        }

        Instant trialEndsAt = Instant.now().plus(trialDuration);
        BillingSubscription subscription = BillingSubscription.builder()
                .billingAccount(billingAccount)
                .status(SubscriptionStatus.TRIAL)
                .cycle(BillingCycle.MONTHLY)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(trialEndsAt)
                .totalCents(0L)
                .build();
        
        return repository.save(subscription);
    }

    @Transactional
    public void addItem(BillingSubscription subscription, BillingSubscriptionItemSourceType sourceType, String sourceId, BillingPlan plan) {
        log.info("Adding item to subscription {}: type={}, id={}, plan={}", subscription.getId(), sourceType, sourceId, plan.getCode());
        
        BillingSubscriptionItem item = BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .plan(plan)
                .valueCents(plan.getPriceCents().longValue())
                .build();
        
        itemRepository.save(item);

        if (subscription.getItems() != null && !subscription.getItems().contains(item)) {
            subscription.addItem(item);
        }

        // Update total value of subscription
        Long newTotal = itemRepository.findAllByBillingSubscriptionId(subscription.getId())
                .stream()
                .mapToLong(BillingSubscriptionItem::getValueCents)
                .sum();
        
        subscription.setTotalCents(newTotal);
        repository.save(subscription);
    }

    @Transactional(readOnly = true)
    public BillingSubscription findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Billing subscription not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<BillingSubscription> findByUser(Long userId) {
        return repository.findByBillingAccountUserId(userId);
    }

}
