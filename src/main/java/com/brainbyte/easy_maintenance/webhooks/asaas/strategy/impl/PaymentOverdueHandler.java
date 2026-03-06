package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
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
public class PaymentOverdueHandler extends AbstractAsaasWebhookStrategy {

    public PaymentOverdueHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                                 InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                                 BillingSubscriptionRepository billingSubscriptionRepository,
                                 BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                 InvoiceItemRepository invoiceItemRepository,
                                 OrganizationRepository organizationRepository,
                                 ObjectMapper objectMapper) {

        super(invoiceService, paymentRepository, invoiceRepository, billingAccountRepository,
                billingSubscriptionRepository, billingSubscriptionItemRepository, invoiceItemRepository,
                organizationRepository, objectMapper);

    }

    @Override
    public String getEventType() {
        return "PAYMENT_OVERDUE";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());
        
        var paymentObj = event.payment();
        if (paymentObj == null) {
            log.info("[AsaasWebhook] Event {}/{} finished (payment object is null).", event.id(), event.event());
            return;
        }

        var paymentOpt = paymentRepository.findByExternalPaymentId(paymentObj.id());
        
        if (paymentOpt.isEmpty()) {
            log.info("[AsaasWebhook] Payment with external_payment_id {} not found. Skipping PAYMENT_OVERDUE processing.", paymentObj.id());

            return;
        }

        var payment = paymentOpt.get();

        if (payment.getStatus().isFinal() || payment.getStatus() == PaymentStatus.RECEIVED) {
            log.info("[AsaasWebhook] Payment {} is already in a final state ({}). Skipping update.", payment.getId(), payment.getStatus());
            return;
        }

        payment.setStatus(PaymentStatus.OVERDUE);
        payment.setRawPayloadJson(serializeWebhookEvent(event));
        paymentRepository.save(payment);

        Invoice invoice = payment.getInvoice();
        invoice.setStatus(InvoiceStatus.OVERDUE);
        invoiceRepository.save(invoice);

        log.info("[AsaasWebhook] Payment {} marked as OVERDUE and Invoice {} as OVERDUE", payment.getId(), invoice.getId());

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());
    }

}
