package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.helper.DateUtils;
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

@Slf4j
@Component
public class CheckoutPaidHandler extends AbstractAsaasWebhookStrategy {

    public CheckoutPaidHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                               PaymentGatewayEventRepository paymentGatewayEventRepository,
                               InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                               BillingSubscriptionRepository billingSubscriptionRepository,
                               BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                               InvoiceItemRepository invoiceItemRepository,
                               OrganizationRepository organizationRepository,
                               ObjectMapper objectMapper) {

        super(invoiceService, paymentRepository, paymentGatewayEventRepository, invoiceRepository, billingAccountRepository,
                billingSubscriptionRepository, billingSubscriptionItemRepository, invoiceItemRepository,
                organizationRepository, objectMapper);

    }

    @Override
    public String getEventType() {
        return "CHECKOUT_PAID";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {} type {} started.", event.id(), event.event());
        
        log.info("[CHECKOUT_PAID] - Iniciando fluxo para checkout com event: {}", event.event());
        if (event.checkout() == null) {
            saveGatewayEvent(event, null);
            log.info("[AsaasWebhook] Event {}/{} finished (checkout object is null).", event.id(), event.event());
            return;
        }

        var paymentOpt = paymentRepository.findByExternalPaymentId(event.checkout().id());
        Long internalPaymentId = paymentOpt.map(Payment::getId).orElse(null);
        saveGatewayEvent(event, internalPaymentId);

        paymentOpt.ifPresentOrElse(payment -> {

            if (payment.getStatus().isFinal()) {
                log.info("[AsaasWebhook] Checkout {} already finalized, ignoring", event.checkout().id());
                return;
            }

            payment.setStatus(PaymentStatus.CHECKOUT_PAID);
            payment.setPaidAt(DateUtils.parseEventDate(event.dateCreated()));
            payment.setRawPayloadJson(serializeWebhookEvent(event));
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);

            updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.PENDING_ACTIVATION);

            log.info("[AsaasWebhook] Checkout {} marked as PAID", event.checkout().id());

        }, () -> log.error("[CHECKOUT_PAID] - Pagamento não encontrado com id {}", event.checkout().id()));

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());
    }
}
