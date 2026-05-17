package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingReconciliationService {

    static final String METRIC_NAME = "billing.reconciliation.divergence.count";
    private static final Set<String> ASAAS_RECEIVED_STATUSES = Set.of("RECEIVED", "CONFIRMED");

    private final BillingSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final AsaasClient asaasClient;
    private final MeterRegistry meterRegistry;

    @Value("${billing.reconciliation.window-days:90}")
    private int windowDays;

    public void reconcile() {
        Instant createdAfter = Instant.now().minus(windowDays, ChronoUnit.DAYS);
        List<SubscriptionStatus> statuses = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

        List<BillingSubscription> candidates = subscriptionRepository.findReconciliationCandidates(statuses, createdAfter);
        log.info("[BillingReconciliation] Starting. candidates={}, windowDays={}", candidates.size(), windowDays);

        int paymentFixed = 0;
        int subscriptionFixed = 0;

        for (BillingSubscription sub : candidates) {
            try {
                if (sub.getExternalSubscriptionId() != null && checkAndFixCanceledSubscription(sub)) {
                    subscriptionFixed++;
                    continue;
                }
                paymentFixed += checkAndFixPendingPayments(sub);
            } catch (Exception e) {
                log.error("[BillingReconciliation] Error processing subscriptionId={}: {}", sub.getId(), e.getMessage());
            }
        }

        log.info("[BillingReconciliation] Done. paymentsFixed={}, subscriptionsFixed={}", paymentFixed, subscriptionFixed);
    }

    @Transactional
    public boolean checkAndFixCanceledSubscription(BillingSubscription sub) {
        var asaasSub = asaasClient.getSubscription(sub.getExternalSubscriptionId());
        if (asaasSub == null || !"INACTIVE".equals(asaasSub.status())) {
            return false;
        }
        log.warn("[BillingReconciliation] Divergence (subscription_canceled): subscriptionId={} is {} locally but INACTIVE in Asaas. Correcting.",
                sub.getId(), sub.getStatus());
        sub.setStatus(SubscriptionStatus.CANCELED);
        subscriptionRepository.save(sub);
        meterRegistry.counter(METRIC_NAME, "type", "subscription_canceled").increment();
        return true;
    }

    @Transactional
    public int checkAndFixPendingPayments(BillingSubscription sub) {
        List<Payment> pending = paymentRepository.findByBillingSubscriptionIdAndStatus(sub.getId(), PaymentStatus.PENDING);
        int fixed = 0;
        for (Payment payment : pending) {
            if (payment.getExternalPaymentId() == null) {
                continue;
            }
            try {
                var asaasPayment = asaasClient.getPayment(payment.getExternalPaymentId());
                if (asaasPayment != null && ASAAS_RECEIVED_STATUSES.contains(asaasPayment.status())) {
                    log.warn("[BillingReconciliation] Divergence (payment_received): paymentId={} is PENDING locally but {} in Asaas. Correcting.",
                            payment.getId(), asaasPayment.status());
                    payment.setStatus(PaymentStatus.RECEIVED);
                    paymentRepository.save(payment);
                    meterRegistry.counter(METRIC_NAME, "type", "payment_received").increment();
                    fixed++;
                }
            } catch (Exception e) {
                log.error("[BillingReconciliation] Error fetching Asaas payment externalId={}: {}",
                        payment.getExternalPaymentId(), e.getMessage());
            }
        }
        return fixed;
    }
}
