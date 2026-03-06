package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Component
public class PaymentReceivedHandler extends AbstractAsaasWebhookStrategy {

    public PaymentReceivedHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                                  InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                                  BillingSubscriptionRepository billingSubscriptionRepository,
                                  BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                  InvoiceItemRepository invoiceItemRepository,
                                  OrganizationRepository organizationRepository,
                                  ObjectMapper objectMapper,
                                  BillingSubscriptionService billingSubscriptionService) {

        super(invoiceService, paymentRepository, invoiceRepository, billingAccountRepository,
                billingSubscriptionRepository,
                billingSubscriptionItemRepository, invoiceItemRepository, organizationRepository, objectMapper);
        this.billingSubscriptionService = billingSubscriptionService;

    }

    private final BillingSubscriptionService billingSubscriptionService;

    @Override
    public String getEventType() {
        return "PAYMENT_RECEIVED";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());
        
        var paymentObj = event.payment();
        if (paymentObj == null) {
            log.info("[AsaasWebhook] Event {}/{} finished (payment object is null).", event.id(), event.event());
            return;
        }

        // 1. Tentar encontrar pelo externalPaymentId
        var paymentOpt = paymentRepository.findByExternalPaymentId(paymentObj.id());
        
        // 2. Se não encontrar e for checkout, tentar por externalCheckoutId
        if (paymentOpt.isEmpty() && event.checkout() != null) {
            paymentOpt = paymentRepository.findByExternalCheckoutId(event.checkout().id());
        }
        
        if (paymentOpt.isEmpty()) {
            log.warn("[AsaasWebhook] Payment with external_payment_id {} (or checkout_id) not found. Skipping PAYMENT_RECEIVED processing.", paymentObj.id());
            return;
        }

        var payment = paymentOpt.get();
        if (payment.getExternalPaymentId() == null) {
            payment.setExternalPaymentId(paymentObj.id());
        }

        if (payment.getStatus().isFinal() || payment.getStatus() == PaymentStatus.RECEIVED) {
            log.info("[AsaasWebhook] Payment {} is already in a final state ({}). Skipping update.", payment.getId(), payment.getStatus());
            return;
        }

        payment.setStatus(PaymentStatus.RECEIVED);
        
        LocalDate paidDate = paymentObj.paymentDate() != null ? paymentObj.paymentDate() : paymentObj.confirmedDate();
        if (paidDate != null) {
            payment.setPaidAt(paidDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        } else {
            payment.setPaidAt(Instant.now());
        }

        if (paymentObj.invoiceUrl() != null) {
            payment.setPaymentLink(paymentObj.invoiceUrl());
        }

        payment.setRawPayloadJson(serializeWebhookEvent(event));
        paymentRepository.save(payment);

        Invoice invoice = payment.getInvoice();
        invoice.setStatus(InvoiceStatus.PAID);
        invoiceRepository.save(invoice);

        billingSubscriptionRepository.findByBillingAccountUserId(payment.getPayer().getId()).ifPresent(bs -> {
            bs.setStatus(SubscriptionStatus.ACTIVE);
            
            // Apply pending plans if this payment was for an upgrade prorata
            if (payment.getExternalReference() != null && payment.getExternalReference().startsWith("UPGRADE-")) {
                billingSubscriptionService.applyPendingPlans(bs);
            }
            
            billingSubscriptionRepository.save(bs);
        });

        updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.ACTIVE);

        log.info("[AsaasWebhook] Payment {} marked as RECEIVED and Invoice {} as PAID", payment.getId(), invoice.getId());
        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());

    }

}
