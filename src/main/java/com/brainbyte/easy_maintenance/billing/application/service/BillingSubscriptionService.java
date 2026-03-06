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
    public void applyPendingPlans(BillingSubscription billingSubscription) {
        log.info("Applying pending plans for billing subscription {}", billingSubscription.getId());

        boolean updated = false;
        if (billingSubscription.getNextPlanUser() != null) {
            updateItemPlan(billingSubscription, BillingSubscriptionItemSourceType.USER, billingSubscription.getNextPlanUser());
            updated = true;
        }

        if (billingSubscription.getNextPlanOrg() != null) {
            updateItemPlan(billingSubscription, BillingSubscriptionItemSourceType.ORGANIZATION, billingSubscription.getNextPlanOrg());
            updated = true;
        }

        if (updated) {
            recalculateTotal(billingSubscription);
            updateAsaasSubscription(billingSubscription);
            billingSubscription.applyPendingPlans();
            repository.save(billingSubscription);
        }
    }

    private void updateItemPlan(BillingSubscription subscription, BillingSubscriptionItemSourceType type, BillingPlan newPlan) {
        List<BillingSubscriptionItem> items = itemRepository.findAllByBillingSubscriptionId(subscription.getId());
        items.stream()
                .filter(i -> i.getSourceType() == type)
                .forEach(i -> {
                    i.setPlan(newPlan);
                    i.setValueCents(newPlan.getPriceCents().longValue());
                    itemRepository.save(i);
                });
    }

    private void recalculateTotal(BillingSubscription subscription) {
        Long newTotal = itemRepository.findAllByBillingSubscriptionId(subscription.getId())
                .stream()
                .mapToLong(BillingSubscriptionItem::getValueCents)
                .sum();
        subscription.setTotalCents(newTotal);
    }

    private void updateAsaasSubscription(BillingSubscription subscription) {
        if (subscription.getExternalSubscriptionId() == null) return;

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

    @Transactional
    public void updateStatus(Long id, Consumer<BillingSubscription> statusUpdater) {
        log.info("Updating status for billing subscription {}", id);
        BillingSubscription subscription = findById(id);
        statusUpdater.accept(subscription);
        repository.save(subscription);
    }
}
