package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.PaymentMethodTransitionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.AsaasException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardTransitionService {

    private final BillingSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final AsaasClient asaasClient;
    private final AsaasProperties asaasProperties;
    private final PaymentMethodTransitionService transitionService;

    public void processCardTransitions(int daysAhead) {
        Instant upperBound = Instant.now().plus(daysAhead, ChronoUnit.DAYS);
        log.info("[CardTransition] Searching ACTIVE CARD subscriptions without externalSubscriptionId, currentPeriodEnd <= {} ({} days ahead)",
                upperBound, daysAhead);

        List<BillingSubscription> eligible = subscriptionRepository.findPendingCardTransitions(upperBound);
        log.info("[CardTransition] Found {} subscription(s) pending CC checkout creation", eligible.size());

        for (BillingSubscription sub : eligible) {
            try {
                processTransition(sub);
            } catch (Exception e) {
                log.error("[CardTransition] Failed to process CC transition for subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void processTransition(BillingSubscription subscription) {
        BillingAccount account = subscription.getBillingAccount();
        Long userId = account.getUser().getId();
        Long subscriptionId = subscription.getId();

        List<Payment> pending = paymentRepository.findByBillingSubscriptionIdAndStatus(subscriptionId, PaymentStatus.PENDING);
        if (!pending.isEmpty()) {
            log.info("[CardTransition] Subscription {} has pending payment — skipping CC checkout creation (PIX cycle still active)", subscriptionId);
            return;
        }

        LocalDate nextDueDate = resolveDueDate(subscription);

        Invoice invoice = invoiceService
                .generateInvoiceForPayer(userId, nextDueDate, nextDueDate.plusMonths(1).minusDays(1))
                .orElse(null);
        if (invoice == null || invoice.getItems().isEmpty()) {
            log.warn("[CardTransition] No invoice generated for subscription {} (userId={}). Skipping.", subscriptionId, userId);
            return;
        }

        String externalReference = PaymentMethodTransitionService.CARD_UPDATE_PREFIX + subscriptionId + "-CYCLE-" + UUID.randomUUID();

        AsaasDTO.CreateCheckoutRequest req = transitionService.buildCheckoutRequest(account, invoice, nextDueDate, subscriptionId, externalReference);

        AsaasDTO.CheckoutResponse resp;
        try {
            resp = asaasClient.createCheckout(req);
        } catch (AsaasException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CardTransition] Failed to create CC checkout for subscription {} (userId={}): {}", subscriptionId, userId, e.getMessage());
            throw new AsaasException("Failed to create CC checkout for subscription " + subscriptionId, e);
        }

        Payment payment = Payment.builder()
                .invoice(invoice)
                .billingSubscription(subscription)
                .payer(account.getUser())
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .externalReference(externalReference)
                .externalPaymentId(resp.id())
                .paymentLink(resp.link())
                .build();

        paymentRepository.save(payment);

        log.info("[CardTransition] CC checkout created for PIX→CC transition: subscriptionId={}, userId={}, checkoutId={}",
                subscriptionId, userId, resp.id());
    }

    private LocalDate resolveDueDate(BillingSubscription subscription) {
        if (subscription.getCurrentPeriodEnd() != null) {
            LocalDate periodEndDate = subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate today = LocalDate.now();
            return periodEndDate.isBefore(today) ? today : periodEndDate;
        }
        return LocalDate.now();
    }
}
