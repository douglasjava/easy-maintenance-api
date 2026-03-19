package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentGatewayEventRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PaymentCreatedHandler extends AbstractAsaasWebhookStrategy {

    public PaymentCreatedHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
                                 PaymentGatewayEventRepository paymentGatewayEventRepository,
                                 InvoiceRepository invoiceRepository, BillingAccountRepository billingAccountRepository,
                                 BillingSubscriptionRepository billingSubscriptionRepository,
                                 BillingSubscriptionItemRepository billingSubscriptionItemRepository,
                                 InvoiceItemRepository invoiceItemRepository,
                                 OrganizationRepository organizationRepository,
                                 ObjectMapper objectMapper) {

        super(invoiceService, paymentRepository, paymentGatewayEventRepository, invoiceRepository,
                billingAccountRepository, billingSubscriptionRepository, billingSubscriptionItemRepository,
                invoiceItemRepository, organizationRepository, objectMapper);

    }

    @Override
    public String getEventType() {
        return "PAYMENT_CREATED";
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

        // 1. Localizar o payment existente usando externalReference ou externalPaymentId (checkoutSession no Asaas)
        Optional<Payment> paymentOpt = Optional.empty();

        if (paymentObj.externalReference() != null) {
            paymentOpt = paymentRepository.findByExternalReference(paymentObj.externalReference());
        }

        if (paymentOpt.isEmpty() && paymentObj.checkoutSession() != null) {
            paymentOpt = paymentRepository.findByExternalPaymentId(paymentObj.checkoutSession());
        }

        if (paymentOpt.isEmpty()) {
            paymentOpt = paymentRepository.findByExternalPaymentId(paymentObj.id());
        }

        Long internalPaymentId = paymentOpt.map(Payment::getId).orElse(null);
        saveGatewayEvent(event, internalPaymentId);

        if (paymentOpt.isPresent()) {
            var payment = paymentOpt.get();

            payment.setExternalPaymentId(paymentObj.id());
            payment.setGatewayStatus(paymentObj.status());
            payment.setPaymentLink(paymentObj.invoiceUrl());
            payment.setReceiptUrl(paymentObj.transactionReceiptUrl());
            payment.setInvoiceNumber(paymentObj.invoiceNumber());

            if (paymentObj.netValue() != null) {
                payment.setNetAmountCents(paymentObj.netValue().movePointRight(2).intValueExact());
                if (paymentObj.value() != null) {
                    BigDecimal fee = paymentObj.value().subtract(paymentObj.netValue());
                    payment.setGatewayFeeCents(fee.movePointRight(2).intValueExact());
                }
            }

            if ("CONFIRMED".equals(paymentObj.status()) || "RECEIVED".equals(paymentObj.status())) {
                payment.setStatus(PaymentStatus.PAID);
                if (paymentObj.confirmedDate() != null) {
                    payment.setPaidAt(paymentObj.confirmedDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                }
            } else {
                payment.setStatus(mapAsaasStatusToPaymentStatus(paymentObj.status()));
            }

            paymentRepository.save(payment);
            log.info("[AsaasWebhook] Payment {} updated for internal id {}", paymentObj.id(), payment.getId());
        } else {
            log.warn("[AsaasWebhook] Payment not found for external id {} or reference {}", paymentObj.id(), paymentObj.externalReference());
        }

        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());
    }

    private PaymentStatus mapAsaasStatusToPaymentStatus(String asaasStatus) {
        if (asaasStatus == null) return PaymentStatus.PENDING;

        return switch (asaasStatus.toUpperCase()) {
            case "RECEIVED", "CONFIRMED" -> PaymentStatus.PAID;
            case "OVERDUE" -> PaymentStatus.OVERDUE;
            case "REFUNDED" -> PaymentStatus.REFUNDED;
            default -> PaymentStatus.PENDING;
        };
    }

}
