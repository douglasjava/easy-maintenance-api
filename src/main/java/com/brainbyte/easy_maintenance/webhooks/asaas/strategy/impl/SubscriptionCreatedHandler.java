package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.PaymentMethodTransitionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentGatewayEventRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Slf4j
@Component
public class SubscriptionCreatedHandler extends AbstractAsaasWebhookStrategy {

    private final AsaasClient asaasClient;

    public SubscriptionCreatedHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                                     PaymentGatewayEventRepository paymentGatewayEventRepository,
                                     InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                                     BillingSubscriptionRepository billingSubscriptionRepository,
                                     BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                     InvoiceItemRepository invoiceItemRepository,
                                     OrganizationRepository organizationRepository,
                                     ObjectMapper objectMapper,
                                     AsaasClient asaasClient) {
        super(invoiceService, paymentRepository, paymentGatewayEventRepository, invoiceRepository, billingAccountRepository,
                billingSubscriptionRepository, billingSubscriptionItemRepository, invoiceItemRepository,
                organizationRepository, objectMapper);
        this.asaasClient = asaasClient;
    }

    @Override
    public String getEventType() {
        return "SUBSCRIPTION_CREATED";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());

        if (event.subscription() == null) {
            saveGatewayEvent(event, null);
            log.warn("[AsaasWebhook] Event {}/{} finished (subscription object is null).", event.id(), event.event());
            return;
        }

        var asaasSub = event.subscription();
        String ref = asaasSub.checkoutSession();

        var paymentOpt = paymentRepository.findByExternalPaymentId(ref);
        Long internalPaymentId = paymentOpt.map(Payment::getId).orElse(null);
        saveGatewayEvent(event, internalPaymentId);

        if (asaasSub.deleted()) {
            log.warn("[AsaasWebhook] Subscription {} is deleted, ignoring.", asaasSub.id());
            return;
        }

        if (!"ACTIVE".equalsIgnoreCase(asaasSub.status())) {
            log.warn("[AsaasWebhook] Subscription {} returned with status {}, ignoring activation.",
                    asaasSub.id(), asaasSub.status());
            return;
        }

        if (Objects.isNull(ref)) {
            log.warn("[AsaasWebhook] Subscription {} has invalid externalReference: {}", asaasSub.id(), ref);
            return;
        }

        paymentOpt.ifPresent(payment -> {

            var subscription = payment.getBillingSubscription();
            if (subscription.getExternalSubscriptionId() != null) {
                if (isCardUpdate(payment)) {
                    handleCardUpdate(subscription, asaasSub, payment);
                } else {
                    log.info("[AsaasWebhook] Subscription {} already linked, ignoring", subscription.getId());
                }
                return;
            }

            log.info(
                    "[AsaasWebhook] Linking external subscription {} to BillingSubscription {} via checkout {}",
                    asaasSub.id(),
                    subscription.getId(),
                    ref
            );

            subscription.activate(asaasSub.id(), asaasSub.nextDueDate());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            billingSubscriptionRepository.save(subscription);

            log.info("[AsaasWebhook] BillingSubscription {} activated and status propagated.", subscription.getId());

        });

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());

    }

    private boolean isCardUpdate(Payment payment) {
        return payment.getExternalReference() != null
                && payment.getExternalReference().startsWith(PaymentMethodTransitionService.CARD_UPDATE_PREFIX);
    }

    private void handleCardUpdate(BillingSubscription subscription, AsaasDTO.WebhookSubscription asaasSub, Payment payment) {
        String oldExternalSubId = subscription.getExternalSubscriptionId();
        log.info("[AsaasWebhook] CC→CC card update: replacing externalSubscriptionId {} → {} for BillingSubscription {}",
                oldExternalSubId, asaasSub.id(), subscription.getId());

        subscription.activate(asaasSub.id(), asaasSub.nextDueDate());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        billingSubscriptionRepository.save(subscription);

        try {
            asaasClient.cancelSubscription(oldExternalSubId);
            log.info("[AsaasWebhook] Old Asaas subscription {} canceled after card update.", oldExternalSubId);
        } catch (Exception e) {
            log.warn("[AsaasWebhook] Could not cancel old Asaas subscription {} after card update (may already be gone): {}",
                    oldExternalSubId, e.getMessage());
        }

        log.info("[AsaasWebhook] Card update complete for BillingSubscription {}. New externalSubscriptionId={}",
                subscription.getId(), asaasSub.id());
    }

}
