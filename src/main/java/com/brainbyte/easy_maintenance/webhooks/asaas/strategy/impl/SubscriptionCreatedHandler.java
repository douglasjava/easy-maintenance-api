package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SubscriptionCreatedHandler extends AbstractAsaasWebhookStrategy {

    public SubscriptionCreatedHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
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
        return "SUBSCRIPTION_CREATED";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());

        if (event.subscription() == null) {
            log.warn("[AsaasWebhook] Event {}/{} finished (subscription object is null).", event.id(), event.event());
            return;
        }

        var asaasSub = event.subscription();
        String ref = asaasSub.externalReference();

        if (ref == null || !ref.startsWith("BILLING-")) {
            log.warn("[AsaasWebhook] Subscription {} has invalid externalReference: {}", asaasSub.id(), ref);
            return;
        }

        Long billingId = Long.valueOf(ref.replace("BILLING-", ""));

        billingSubscriptionRepository.findById(billingId).ifPresent(subscription -> {
            if (subscription.getExternalSubscriptionId() != null) {
                log.info("[AsaasWebhook] Subscription {} already linked, ignoring", subscription.getId());
                return;
            }

            log.info("[AsaasWebhook] Linking external subscription {} to BillingSubscription {}", asaasSub.id(), billingId);
            subscription.activate(asaasSub.id(), asaasSub.nextDueDate());
            billingSubscriptionRepository.save(subscription);

            updateSubscriptions(subscription.getBillingAccount().getUser().getId(), SubscriptionStatus.ACTIVE);
            log.info("[AsaasWebhook] BillingSubscription {} activated and status propagated.", billingId);
        });

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());
    }

}
