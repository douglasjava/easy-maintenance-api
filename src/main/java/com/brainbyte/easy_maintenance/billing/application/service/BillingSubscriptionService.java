package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAdminDTO;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingSubscriptionService {

    private final BillingSubscriptionRepository repository;
    private final BillingSubscriptionItemRepository itemRepository;
    private final AsaasClient asaasClient;
    private final BillingNotificationService billingNotificationService;

    @Transactional(readOnly = true)
    public PageResponse<BillingAdminDTO.SubscriptionResponse> listSubscriptions(
            String planCode,
            String payerName,
            SubscriptionStatus status,
            Pageable pageable) {

        Specification<BillingSubscriptionItem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("billingSubscription", jakarta.persistence.criteria.JoinType.LEFT)
                        .fetch("billingAccount", jakarta.persistence.criteria.JoinType.LEFT);
                root.fetch("plan", jakarta.persistence.criteria.JoinType.LEFT);
            }

            Join<BillingSubscriptionItem, BillingSubscription> subscriptionJoin = root.join("billingSubscription");
            Join<BillingSubscription, BillingAccount> accountJoin = subscriptionJoin.join("billingAccount");

            if (planCode != null && !planCode.isBlank()) {
                predicates.add(cb.equal(root.get("plan").get("code"), planCode));
            }

            if (status != null) {
                predicates.add(cb.equal(subscriptionJoin.get("status"), status));
            }

            if (payerName != null && !payerName.isBlank()) {
                predicates.add(cb.like(cb.lower(accountJoin.get("name")), "%" + payerName.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var page = itemRepository.findAll(spec, pageable);

        var content = page.getContent().stream()
                .map(item -> {
                    BillingAccount billingAccount = item.getBillingSubscription().getBillingAccount();
                    return new BillingAdminDTO.SubscriptionResponse(
                            item.getId(),
                            item.getBillingSubscription().getId(),
                            item.getSourceType(),
                            item.getPlan().getCode(),
                            billingAccount.getId(),
                            billingAccount.getName(),
                            billingAccount.getUser().getId(),
                            item.getBillingSubscription().getStatus(),
                            item.getBillingSubscription().getCurrentPeriodStart(),
                            item.getBillingSubscription().getCurrentPeriodEnd(),
                            item.getBillingSubscription().getTotalCents()
                    );
                })
                .toList();

        return new PageResponse<>(
                content,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional
    public void applyPendingPlans(BillingSubscriptionItem billingSubscriptionItem) {
        log.info("Applying pending plans for billing subscription {}", billingSubscriptionItem.getId());

        var billingSubscription = billingSubscriptionItem.getBillingSubscription();

        boolean updated = false;
        if (billingSubscriptionItem.getNextPlan() != null) {
            billingSubscriptionItem.setPlan(billingSubscriptionItem.getNextPlan());
            billingSubscriptionItem.setValueCents(billingSubscriptionItem.getNextPlan().getPriceCents().longValue());
            billingSubscriptionItem.setNextPlan(null);
            billingSubscriptionItem.setPlanChangeEffectiveAt(null);
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

        BillingSubscriptionItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada"));

        BillingSubscription subscription = item.getBillingSubscription();

        // CASE 1: USER item -> full cancellation
        if (item.getSourceType() == BillingSubscriptionItemSourceType.USER) {

            if (subscription.isCancelAtPeriodEnd()) {
                log.info("Subscription {} already scheduled for cancellation", subscription.getId());
                return;
            }

            subscription.setCancelAtPeriodEnd(true);
            subscription.setStatus(SubscriptionStatus.CANCELED);
            repository.save(subscription);

            log.info("Full subscription cancellation scheduled for subscription {}", subscription.getId());

            return;
        }

        // CASE 2: ORGANIZATION item -> partial cancellation
        if (item.isCancelAtPeriodEnd()) {
            log.info("Item {} already scheduled for cancellation", item.getId());
            return;
        }

        item.setCancelAtPeriodEnd(true);
        itemRepository.save(item);

        log.info("Item {} scheduled for cancellation at period end", item.getId());

    }

    @Transactional
    public void undoItemCancellation(Long itemId) {
        log.info("Undoing cancellation for subscription item: {}", itemId);

        BillingSubscriptionItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NotFoundException("Item not found"));

        BillingSubscription subscription = item.getBillingSubscription();

        if (item.getSourceType() == BillingSubscriptionItemSourceType.USER) {
            subscription.setCancelAtPeriodEnd(false);
            repository.save(subscription);
            log.info("Subscription cancellation undone for subscription {}", subscription.getId());
            return;
        }

        item.setCancelAtPeriodEnd(false);
        itemRepository.save(item);
        log.info("Item cancellation undone for item {}", item.getId());
    }

    @Transactional
    public void processSubscriptionCycle() {
        log.info("Starting processing of subscription cycle for {}", LocalDate.now());

        List<BillingSubscription> subscriptions = repository.findAllByNextDueDate(LocalDate.now());

        for (BillingSubscription sub : subscriptions) {

            log.info("Processing subscription {}", sub.getId());

            List<BillingSubscriptionItem> items = itemRepository.findAllByBillingSubscriptionId(sub.getId());

            // STEP 1 — handle full cancellation
            if (sub.isCancelAtPeriodEnd()) {

                sub.setStatus(SubscriptionStatus.CANCELED);
                sub.setCanceledAt(Instant.now());

                repository.save(sub);

                log.info("Subscription {} canceled at cycle end", sub.getId());

                cancelAsaasSubscription(sub);
                billingNotificationService.sendCancellationProcessedEmail(sub);

                continue;
            }

            // STEP 2 — canceled items
            List<BillingSubscriptionItem> toRemove = items.stream()
                    .filter(BillingSubscriptionItem::isCancelAtPeriodEnd)
                    .toList();

            for (BillingSubscriptionItem item : toRemove) {
                item.setCancelAtPeriodEnd(false);
                item.setCanceledAt(Instant.now());
                itemRepository.save(item);
                log.info("Removed item {} at cycle end", item.getId());
            }

            // STEP 3 — recalculate total
            recalculateTotal(sub);

            // STEP 4 — update Asaas subscription value
            updateAsaasSubscription(sub);

            repository.save(sub);

            log.info("Subscription {} updated for next cycle", sub.getId());
        }
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
                .filter(item -> item.getCanceledAt() == null)
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
        if (subscription.getExternalSubscriptionId() == null) return;
        
        try {
            asaasClient.cancelSubscription(subscription.getExternalSubscriptionId());
            log.info("Asaas subscription {} canceled", subscription.getExternalSubscriptionId());
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
