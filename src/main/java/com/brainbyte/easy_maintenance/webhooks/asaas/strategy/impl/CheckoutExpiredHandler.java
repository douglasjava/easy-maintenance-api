package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

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

@Slf4j
@Component
public class CheckoutExpiredHandler extends AbstractAsaasWebhookStrategy {

    public CheckoutExpiredHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                                  InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                                  BillingSubscriptionRepository billingSubscriptionRepository,
                                  BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                  InvoiceItemRepository invoiceItemRepository,
                                  OrganizationRepository organizationRepository,
                                  ObjectMapper objectMapper) {

           super(invoiceService, paymentRepository, invoiceRepository, billingAccountRepository,
                   billingSubscriptionRepository, billingSubscriptionItemRepository,
                   invoiceItemRepository, organizationRepository, objectMapper);

    }

    @Override
    public String getEventType() {
        return "CHECKOUT_EXPIRED";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());
        
        if (event.checkout() == null) {
            log.info("[AsaasWebhook] Event {}/{} finished (checkout object is null).", event.id(), event.event());
            return;
        }

        paymentRepository.findByExternalPaymentId(event.checkout().id()).ifPresentOrElse(payment -> {

            if (payment.getStatus().isFinal()) {
                log.info("[AsaasWebhook] Checkout {} already finalized, ignoring", event.checkout().id());
                return;
            }

            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setFailureReason(getEventType());
            payment.setRawPayloadJson(serializeWebhookEvent(event));
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.CANCELED);
            invoiceRepository.save(invoice);

            updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.PAYMENT_FAILED);
            log.info("[AsaasWebhook] Checkout {} marked as EXPIRED/CANCELED", event.checkout().id());
        }, () -> log.warn("[AsaasWebhook] Payment for checkout {} not found", event.checkout().id()));

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());
    }
}
