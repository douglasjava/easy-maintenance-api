package com.brainbyte.easy_maintenance.webhooks.asaas.service;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookEvent;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.enums.WebhookEventStatus;
import com.brainbyte.easy_maintenance.webhooks.commons.service.WebhookEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasWebhookService {

    public static final String MSG_CHECKOUT_NOT_FOUND = "[AsaasWebhook] Payment for checkout {} not found";

    private final WebhookEventService webhookEventService;
    private final InvoiceService invoiceService;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingAccountRepository billingAccountRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processEvent(AsaasDTO.WebhookCheckoutEvent event) {
        if (event == null) {
            log.warn("Asaas webhook received null event");
            return;
        }

        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());

        if (webhookEventService.findByProviderEventId(event.id()).isPresent()) {
            log.info("[AsaasWebhook] Event {} already processed. Skipping.", event.id());
            return;
        }

        var rawPayload = serializeWebhookEvent(event);
        var webhookEvent = getWebhookEvent(event, rawPayload);

        webhookEvent = webhookEventService.save(webhookEvent);

        try {
            webhookEvent.setStatus(WebhookEventStatus.PROCESSING);
            webhookEventService.save(webhookEvent);

            switch (event.event()) {
                case "PAYMENT_CREATED" -> handlePaymentCreated(event);
                case "PAYMENT_RECEIVED" -> handlePaymentReceived(event);
                case "PAYMENT_OVERDUE" -> handlePaymentOverdue(event);
                case "CHECKOUT_PAID" -> handleCheckoutPaid(event);
                case "CHECKOUT_EXPIRED" -> handleCheckoutExpired(event);
                default -> log.info("[AsaasWebhook] Event type {} not handled", event.event());
            }

            webhookEvent.setStatus(WebhookEventStatus.PROCESSED);
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventService.save(webhookEvent);
            log.info("[AsaasWebhook] Event {} processed successfully", event.id());

        } catch (Exception e) {
            log.error("[AsaasWebhook] Error processing event {}: {}", event.id(), e.getMessage(), e);
            webhookEvent.setStatus(WebhookEventStatus.ERROR);
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventService.save(webhookEvent);
            throw e;
        }
    }

    private WebhookEvent getWebhookEvent(AsaasDTO.WebhookCheckoutEvent event, String rawPayload) {
        return WebhookEvent.builder()
                .providerEventId(event.id())
                .eventType(event.event())
                .eventCreatedAt(parseEventDate(event.dateCreated()))
                .payload(rawPayload)
                .status(WebhookEventStatus.RECEIVED)
                .build();
    }

    private String serializeWebhookEvent(AsaasDTO.WebhookCheckoutEvent event) {
        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Error serializing asaas event payload", e);
            rawPayload = "Serialization error";
        }
        return rawPayload;
    }

    private void handlePaymentCreated(AsaasDTO.WebhookCheckoutEvent event) {

        var paymentObj = event.payment();
        if (paymentObj == null) return;

        LocalDate start = LocalDate.now().withDayOfMonth(1);
        String customer = paymentObj.customer();

        log.info("Buscando dados da conta do usuário {}", customer);
        var billingAccount = billingAccountRepository.findByExternalCustomerId(customer)
                .orElseThrow(() -> new NotFoundException(String.format("Conta não encontrada para usuário %s", customer)));

        var payerId = billingAccount.getUser().getId();

        log.info("Recuperando a data de start a partir do último invoice gerado (period_end + 1 day)");
        var lastInvoice = invoiceRepository.findFirstByPayerIdOrderByPeriodEndDesc(payerId);

        if (lastInvoice.isPresent()) {
            start = lastInvoice.get().getPeriodEnd().plusDays(1);
        }

        LocalDate end = start.plusMonths(1).minusDays(1);

        invoiceService.generateInvoiceForPayer(payerId, start, end).ifPresentOrElse(invoice -> {
            Payment payment = Payment.builder()
                    .invoice(invoice)
                    .payer(invoice.getPayer())
                    .provider(PaymentProvider.ASAAS)
                    .methodType(parseMethodType(paymentObj.billingType()))
                    .status(PaymentStatus.PENDING)
                    .amountCents(paymentObj.value().multiply(new java.math.BigDecimal(100)).intValue())
                    .externalPaymentId(paymentObj.id())
                    .externalReference(paymentObj.externalReference())
                    .paymentLink(paymentObj.invoiceUrl())
                    .build();

            paymentRepository.save(payment);
            log.info("[AsaasWebhook] Payment created for event {} and payer {}", event.id(), payerId);
        }, () -> log.warn("[AsaasWebhook] No invoice generated for PAYMENT_CREATED event {} and payer {}", event.id(), payerId));

    }

    private void handlePaymentReceived(AsaasDTO.WebhookCheckoutEvent event) {
        var paymentObj = event.payment();
        if (paymentObj == null) return;

        paymentRepository.findByExternalPaymentId(paymentObj.id()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.RECEIVED);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);

            updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.ACTIVE);
            log.info("[AsaasWebhook] Payment {} marked as RECEIVED and Invoice {} as PAID", payment.getId(), invoice.getId());
        });
    }

    private void handlePaymentOverdue(AsaasDTO.WebhookCheckoutEvent event) {
        var paymentObj = event.payment();
        if (paymentObj == null) return;

        paymentRepository.findByExternalPaymentId(paymentObj.id()).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.OVERDUE);
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoiceRepository.save(invoice);

            updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.PAST_DUE);
            log.info("[AsaasWebhook] Payment {} marked as OVERDUE and Invoice {} as OVERDUE", payment.getId(), invoice.getId());
        });
    }

    private void handleCheckoutPaid(AsaasDTO.WebhookCheckoutEvent event) {
        if (event.checkout() == null) return;

        paymentRepository.findByExternalPaymentId(event.checkout().id()).ifPresentOrElse(payment -> {
            payment.setStatus(PaymentStatus.RECEIVED);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.PAID);
            invoiceRepository.save(invoice);

            updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.ACTIVE);
            log.info("[AsaasWebhook] Checkout {} marked as PAID", event.checkout().id());
        }, () -> log.warn(MSG_CHECKOUT_NOT_FOUND, event.checkout().id()));
    }

    private void handleCheckoutExpired(AsaasDTO.WebhookCheckoutEvent event) {
        if (event.checkout() == null) return;

        paymentRepository.findByExternalPaymentId(event.checkout().id()).ifPresentOrElse(payment -> {
            payment.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);

            Invoice invoice = payment.getInvoice();
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoiceRepository.save(invoice);

            updateSubscriptions(payment.getPayer().getId(), SubscriptionStatus.PAST_DUE);
            log.info("[AsaasWebhook] Checkout {} marked as EXPIRED/CANCELED", event.checkout().id());
        }, () -> log.warn(MSG_CHECKOUT_NOT_FOUND, event.checkout().id()));
    }

    private void updateSubscriptions(Long userId, SubscriptionStatus status) {
        organizationSubscriptionRepository.findAllByPayerIdIn(List.of(userId)).forEach(sub -> {
            sub.setStatus(status);
            organizationSubscriptionRepository.save(sub);
        });

        userSubscriptionRepository.findByUserId(userId).ifPresent(sub -> {
            sub.setStatus(status);
            userSubscriptionRepository.save(sub);
        });
    }

    private Instant parseEventDate(String dateStr) {
        try {
            return OffsetDateTime.parse(dateStr).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private PaymentMethodType parseMethodType(String billingType) {
        if (billingType == null) return PaymentMethodType.PIX;
        return switch (billingType) {
            case "BOLETO" -> PaymentMethodType.BOLETO;
            case "CREDIT_CARD" -> PaymentMethodType.CARD;
            default -> PaymentMethodType.PIX;
        };
    }

}
