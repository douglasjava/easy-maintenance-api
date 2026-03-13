package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
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
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AbstractAsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
public class PaymentCreatedHandler extends AbstractAsaasWebhookStrategy {

    public PaymentCreatedHandler(InvoiceService invoiceService, PaymentRepository paymentRepository,
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
        return "PAYMENT_CREATED";
    }

    @Override
    public void handle(AsaasDTO.WebhookCheckoutEvent event) {
        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());

        var paymentObj = event.payment();
        if (paymentObj == null) {
             log.info("[AsaasWebhook] Event {}/{} finished (payment object is null).", event.id(), event.event());
             return;
        }

        // 1. Tentar encontrar pagamento existente pelo externalPaymentId
        var existingPayment = paymentRepository.findByExternalPaymentId(paymentObj.id());
        if (existingPayment.isPresent()) {
            log.info("[AsaasWebhook] Payment {} already processed. Skipping.", paymentObj.id());
            return;
        }

        // 2. Se for checkout, tentar encontrar por externalCheckoutId
        if (event.checkout() != null) {
            var pendingPayment = paymentRepository.findByExternalPaymentId(event.checkout().id());
            if (pendingPayment.isPresent()) {
                var p = pendingPayment.get();
                p.setExternalPaymentId(paymentObj.id());
                p.setRawPayloadJson(serializeWebhookEvent(event));
                if (paymentObj.invoiceUrl() != null) {
                    p.setPaymentLink(paymentObj.invoiceUrl());
                }
                paymentRepository.save(p);
                log.info("[AsaasWebhook] Updated pending payment {} with externalPaymentId {}", p.getId(), paymentObj.id());
                return;
            }
        }

        String customer = paymentObj.customer();
        log.info("Buscando dados da conta do usuário {}", customer);
        var billingAccount = billingAccountRepository.findByExternalCustomerId(customer)
                .orElseThrow(() -> new NotFoundException(String.format("Conta não encontrada para usuário %s", customer)));

        var payerUser = billingAccount.getUser();

        LocalDate dueDate = paymentObj.dueDate();
        BigDecimal value = paymentObj.value();
        Integer totalCents = value.multiply(new BigDecimal(100)).intValue();

        Invoice invoice = Invoice.builder()
                .payer(payerUser)
                .currency("BRL")
                .periodStart(dueDate)
                .periodEnd(dueDate.plusMonths(1).minusDays(1))
                .status(InvoiceStatus.OPEN)
                .dueDate(dueDate)
                .subtotalCents(totalCents)
                .totalCents(totalCents)
                .build();

        invoiceRepository.save(invoice);

        billingSubscriptionRepository.findByBillingAccountUserId(payerUser.getId()).ifPresent(bs -> {
            var bsItems = billingSubscriptionItemRepository.findAllByBillingSubscriptionId(bs.getId());

            for (var bsItem : bsItems) {
                var invoiceItem = InvoiceItem.builder()
                        .invoice(invoice)
                        .plan(bsItem.getPlan())
                        .description(paymentObj.description())
                        .quantity(1)
                        .unitAmountCents(bsItem.getValueCents().intValue())
                        .amountCents(bsItem.getValueCents().intValue())
                        .build();

                invoiceItemRepository.save(invoiceItem);
            }

        });

        Payment payment = Payment.builder()
                .invoice(invoice)
                .payer(payerUser)
                .provider(PaymentProvider.ASAAS)
                .methodType(parseMethodType(paymentObj.billingType()))
                .status(PaymentStatus.PENDING)
                .amountCents(totalCents)
                .currency("BRL")
                .externalPaymentId(paymentObj.id())
                .externalReference(paymentObj.externalReference())
                .paymentLink(paymentObj.invoiceUrl())
                .rawPayloadJson(serializeWebhookEvent(event))
                .build();

        paymentRepository.save(payment);

        log.info("[AsaasWebhook] Invoice {} and Payment created for event {} and payer {}", invoice.getId(), event.id(), payerUser.getId());
        log.info("[AsaasWebhook] Event {}/{} finished.", event.id(), event.event());

    }

}
