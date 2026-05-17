package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentGatewayEventRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.Optional;

@Slf4j
@Component
public class PaymentReceivedHandler extends AbstractAsaasWebhookStrategy {

    private final BillingSubscriptionService billingSubscriptionService;

    public PaymentReceivedHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                                  PaymentGatewayEventRepository paymentGatewayEventRepository,
                                  InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                                  BillingSubscriptionRepository billingSubscriptionRepository,
                                  BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                  InvoiceItemRepository invoiceItemRepository,
                                  OrganizationRepository organizationRepository,
                                  ObjectMapper objectMapper,
                                  BillingSubscriptionService billingSubscriptionService) {

        super(invoiceService, paymentRepository, paymentGatewayEventRepository, invoiceRepository, billingAccountRepository,
                billingSubscriptionRepository, billingSubscriptionItemRepository, invoiceItemRepository,
                organizationRepository, objectMapper);

        this.billingSubscriptionService = billingSubscriptionService;
    }

    @Override
    public String getEventType() {
        return "PAYMENT_RECEIVED";
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
            log.warn("[AsaasWebhook] PAYMENT_RECEIVED: Payment not found for external id {} or reference {}",
                    paymentObj.id(), paymentObj.externalReference());
            return;
        }

        Payment payment = paymentOpt.get();

        if (payment.getStatus() == PaymentStatus.RECEIVED || payment.getStatus() == PaymentStatus.PAID) {
            log.info("[AsaasWebhook] PAYMENT_RECEIVED: Payment {} already settled ({}), skipping (idempotent).",
                    payment.getId(), payment.getStatus());
            return;
        }

        payment.setStatus(PaymentStatus.RECEIVED);
        payment.setGatewayStatus(paymentObj.status());
        if (paymentObj.confirmedDate() != null) {
            payment.setPaidAt(paymentObj.confirmedDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        } else if (paymentObj.paymentDate() != null) {
            payment.setPaidAt(paymentObj.paymentDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        payment.setRawPayloadJson(serializeWebhookEvent(event));
        paymentRepository.save(payment);

        Invoice invoice = payment.getInvoice();
        if (invoice != null) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);
        }

        BillingSubscription subscription = payment.getBillingSubscription();
        if (shouldAdvanceCycle(payment, subscription)) {
            billingSubscriptionService.advanceCycle(subscription, payment);
        } else {
            log.info("[AsaasWebhook] PAYMENT_RECEIVED: cycle advancement skipped for paymentId={} (methodType={}, externalSubscriptionId={})",
                    payment.getId(), payment.getMethodType(),
                    subscription != null ? subscription.getExternalSubscriptionId() : null);
        }

        log.info("[AsaasWebhook] PAYMENT_RECEIVED: Payment {} (cycleNumber={}) marked RECEIVED for subscription {}",
                payment.getId(), payment.getCycleNumber(),
                subscription != null ? subscription.getId() : null);

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());
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

    private boolean shouldAdvanceCycle(Payment payment, BillingSubscription subscription) {
        if (subscription == null) return false;
        if (payment.getMethodType() != PaymentMethodType.PIX) return false;
        // PIX recorrente "manual" is identified by the absence of an Asaas subscription link.
        return subscription.getExternalSubscriptionId() == null;
    }
}
