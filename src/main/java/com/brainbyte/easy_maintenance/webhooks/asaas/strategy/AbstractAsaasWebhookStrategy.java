package com.brainbyte.easy_maintenance.webhooks.asaas.strategy;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentGatewayEventRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAsaasWebhookStrategy implements AsaasWebhookStrategy {

    protected final InvoiceService invoiceService;
    protected final PaymentRepository paymentRepository;
    protected final PaymentGatewayEventRepository paymentGatewayEventRepository;
    protected final InvoiceRepository invoiceRepository;
    protected final BillingAccountRepository billingAccountRepository;
    protected final BillingSubscriptionRepository billingSubscriptionRepository;
    protected final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    protected final InvoiceItemRepository invoiceItemRepository;
    protected final OrganizationRepository organizationRepository;
    protected final ObjectMapper objectMapper;

    protected String serializeWebhookEvent(AsaasDTO.WebhookCheckoutEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Error serializing asaas event payload", e);
            return "Serialization error";
        }
    }

    protected PaymentMethodType parseMethodType(String billingType) {
        if (billingType == null) return PaymentMethodType.PIX;
        return switch (billingType) {
            case "BOLETO" -> PaymentMethodType.BOLETO;
            case "CREDIT_CARD" -> PaymentMethodType.CARD;
            default -> PaymentMethodType.PIX;
        };
    }

    protected void saveGatewayEvent(AsaasDTO.WebhookCheckoutEvent event, Long paymentId) {
        try {
            var payload = objectMapper.writeValueAsString(event);
            var externalId = event.payment() != null ? event.payment().id() : null;

            var gatewayEvent = com.brainbyte.easy_maintenance.payment.domain.PaymentGatewayEvent.builder()
                    .gateway("ASAAS")
                    .eventType(event.event())
                    .externalId(externalId)
                    .paymentId(paymentId)
                    .payloadJson(payload)
                    .build();

            paymentGatewayEventRepository.save(gatewayEvent);
        } catch (Exception e) {
            log.error("[AsaasWebhook] Error saving gateway event", e);
        }
    }

    protected void updateSubscriptions(Long userId, SubscriptionStatus status) {
        log.info("[AsaasWebhook] Propagating status {} for userId {}", status, userId);

        // 1. Atualizar a Assinatura Financeira Central (BillingSubscription)
        var billingSubscription = billingSubscriptionRepository.findByBillingAccountUserId(userId)
                .orElseThrow(() -> new NotFoundException(String.format("Usuário %s não tem assinatura",  userId)));
        billingSubscription.setStatus(status);
        billingSubscriptionRepository.save(billingSubscription);

        log.debug("[AsaasWebhook] Status {} updated for BillingSubscription {}", status, billingSubscription.getId());
    }

}
