package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.error.RefusalBucket;
import com.brainbyte.easy_maintenance.billing.error.RefusalReasonClassifier;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentGatewayEventRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Handles {@code PAYMENT_REFUSED} events from the Asaas webhook.
 *
 * <p>Routes the refusal to one of four actions based on the bucket returned
 * by {@link RefusalReasonClassifier}:
 * <ul>
 *   <li>TRANSIENT   — mark FAILED, log retry hint (no state change on subscription)</li>
 *   <li>USER_ACTION — mark FAILED, move subscription to PAST_DUE, notify user</li>
 *   <li>HARD_FAIL   — mark FAILED, move subscription to CANCELED, alert</li>
 *   <li>INFO/UNKNOWN — mark FAILED, log only</li>
 * </ul>
 */
@Slf4j
@Component
public class PaymentRefusedHandler extends AbstractAsaasWebhookStrategy {

    private final RefusalReasonClassifier classifier;
    private final BillingNotificationService billingNotificationService;
    private final BillingSubscriptionService billingSubscriptionService;

    public PaymentRefusedHandler(InvoiceService invoiceService,
                                 PaymentRepository paymentRepository,
                                 PaymentGatewayEventRepository paymentGatewayEventRepository,
                                 InvoiceRepository invoiceRepository,
                                 BillingAccountRepository billingAccountRepository,
                                 BillingSubscriptionRepository billingSubscriptionRepository,
                                 BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                 InvoiceItemRepository invoiceItemRepository,
                                 OrganizationRepository organizationRepository,
                                 ObjectMapper objectMapper,
                                 RefusalReasonClassifier classifier,
                                 BillingNotificationService billingNotificationService,
                                 BillingSubscriptionService billingSubscriptionService) {
        super(invoiceService, paymentRepository, paymentGatewayEventRepository, invoiceRepository,
                billingAccountRepository, billingSubscriptionRepository, billingSubscriptionItemRepository,
                invoiceItemRepository, organizationRepository, objectMapper);
        this.classifier = classifier;
        this.billingNotificationService = billingNotificationService;
        this.billingSubscriptionService = billingSubscriptionService;
    }

    @Override
    public String getEventType() {
        return "PAYMENT_REFUSED";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());

        var paymentObj = event.payment();
        if (paymentObj == null) {
            saveGatewayEvent(event, null);
            log.info("[AsaasWebhook] Event {}/{} finished (payment object is null).", event.id(), event.event());
            return;
        }

        Optional<Payment> paymentOpt = resolvePayment(paymentObj);
        Long internalPaymentId = paymentOpt.map(Payment::getId).orElse(null);
        saveGatewayEvent(event, internalPaymentId);

        if (paymentOpt.isEmpty()) {
            log.warn("[AsaasWebhook] PAYMENT_REFUSED: Payment not found for externalId={} or reference={}",
                    paymentObj.id(), paymentObj.externalReference());
            return;
        }

        Payment payment = paymentOpt.get();

        if (payment.getStatus().isFinal()) {
            log.info("[AsaasWebhook] PAYMENT_REFUSED: Payment {} already in final state ({}), skipping.",
                    payment.getId(), payment.getStatus());
            return;
        }

        String failureCode = paymentObj.failureReason();
        RefusalBucket bucket = classifier.classify(failureCode);

        payment.setStatus(PaymentStatus.FAILED);
        payment.setGatewayStatus(paymentObj.status());
        payment.setFailureReason(failureCode);
        payment.setRawPayloadJson(serializeWebhookEvent(event));
        paymentRepository.save(payment);

        BillingSubscription subscription = payment.getBillingSubscription();

        switch (bucket) {
            case TRANSIENT -> handleTransient(payment, subscription, failureCode);
            case USER_ACTION -> handleUserAction(payment, subscription);
            case HARD_FAIL -> handleHardFail(payment, subscription);
            default -> log.info("[AsaasWebhook] PAYMENT_REFUSED: bucket={} for paymentId={} — logged only.",
                    bucket, payment.getId());
        }

        log.info("[AsaasWebhook] Event {}/{} finished. paymentId={}, bucket={}", event.id(), event.event(),
                payment.getId(), bucket);
    }

    private void handleTransient(Payment payment, BillingSubscription subscription, String code) {
        log.warn("[AsaasWebhook] PAYMENT_REFUSED TRANSIENT: paymentId={}, code={}, subscriptionId={}. " +
                        "Will retry on next scheduled run.",
                payment.getId(), code, subscription != null ? subscription.getId() : null);
    }

    private void handleUserAction(Payment payment, BillingSubscription subscription) {
        log.warn("[AsaasWebhook] PAYMENT_REFUSED USER_ACTION: paymentId={}, subscriptionId={}. Moving to PAST_DUE.",
                payment.getId(), subscription != null ? subscription.getId() : null);

        if (subscription != null
                && subscription.getStatus() != SubscriptionStatus.PAST_DUE
                && subscription.getStatus() != SubscriptionStatus.CANCELED) {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            billingSubscriptionRepository.save(subscription);
            billingNotificationService.sendSubscriptionBlockedEmail(subscription);
        }
    }

    private void handleHardFail(Payment payment, BillingSubscription subscription) {
        log.error("[AsaasWebhook] PAYMENT_REFUSED HARD_FAIL: paymentId={}, subscriptionId={}. Canceling subscription.",
                payment.getId(), subscription != null ? subscription.getId() : null);

        if (subscription != null && subscription.getStatus() != SubscriptionStatus.CANCELED) {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            billingSubscriptionRepository.save(subscription);
            billingNotificationService.sendCancellationProcessedEmail(subscription);
        }
    }

    private Optional<Payment> resolvePayment(AsaasDTO.PaymentObject paymentObj) {
        Optional<Payment> found = Optional.empty();
        if (paymentObj.externalReference() != null) {
            found = paymentRepository.findByExternalReference(paymentObj.externalReference());
        }
        if (found.isEmpty()) {
            found = paymentRepository.findByExternalPaymentId(paymentObj.id());
        }
        return found;
    }
}
