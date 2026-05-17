package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.AsaasException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class PixRenewalService {

    private final PixRenewalService self;
    private final BillingSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final AsaasClient asaasClient;
    private final BillingNotificationService billingNotificationService;

    public PixRenewalService(@Lazy PixRenewalService self,
                             BillingSubscriptionRepository subscriptionRepository,
                             PaymentRepository paymentRepository,
                             InvoiceService invoiceService,
                             AsaasClient asaasClient,
                             BillingNotificationService billingNotificationService) {
        this.self = self;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.asaasClient = asaasClient;
        this.billingNotificationService = billingNotificationService;
    }

    public void processPixRenewals(int daysAhead) {
        Instant upperBound = Instant.now().plus(daysAhead, ChronoUnit.DAYS);
        log.info("[PixRenewal] Searching ACTIVE PIX subscriptions with currentPeriodEnd <= {} ({} days ahead)",
                upperBound, daysAhead);

        List<BillingSubscription> eligible = subscriptionRepository.findPixSubscriptionsDueForRenewal(upperBound);
        log.info("[PixRenewal] Found {} eligible PIX subscriptions for renewal", eligible.size());

        for (BillingSubscription sub : eligible) {
            try {
                self.renewSubscription(sub.getId());
            } catch (Exception e) {
                log.error("[PixRenewal] Failure renewing subscription {}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void renewSubscription(Long subscriptionId) {
        BillingSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("Subscription not found: " + subscriptionId));

        BillingAccount account = subscription.getBillingAccount();
        Long userId = account.getUser().getId();

        Integer nextCycleNumber = paymentRepository.findMaxCycleNumberByBillingSubscriptionId(subscriptionId) + 1;

        Optional<Payment> existing = paymentRepository
                .findByBillingSubscriptionIdAndCycleNumber(subscriptionId, nextCycleNumber);
        if (existing.isPresent()) {
            log.info("[PixRenewal] Cycle {} already exists for subscription {}, skipping. paymentId={}",
                    nextCycleNumber, subscriptionId, existing.get().getId());
            return;
        }

        LocalDate dueDate = resolveDueDate(subscription);

        Invoice invoice = invoiceService
                .generateInvoiceForPayer(userId, dueDate, dueDate.plusMonths(1).minusDays(1))
                .orElse(null);
        if (invoice == null || invoice.getItems().isEmpty()) {
            log.warn("[PixRenewal] No invoice could be generated for subscription {} (userId={}). Skipping renewal.",
                    subscriptionId, userId);
            return;
        }

        String externalReference = "BILLING-" + subscriptionId + "-CYCLE-" + nextCycleNumber;

        AsaasDTO.PaymentResponse asaasResp = createAsaasPixCharge(account, invoice, externalReference, dueDate);

        Payment payment = Payment.builder()
                .invoice(invoice)
                .billingSubscription(subscription)
                .cycleNumber(nextCycleNumber)
                .payer(account.getUser())
                .provider(PaymentProvider.ASAAS)
                .methodType(account.getPaymentMethod())
                .status(PaymentStatus.PENDING)
                .amountCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .externalReference(externalReference)
                .externalPaymentId(asaasResp.id())
                .paymentLink(asaasResp.invoiceUrl())
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[PixRenewal] Renewal charge created: subscriptionId={}, cycleNumber={}, asaasPaymentId={}, paymentId={}",
                subscriptionId, nextCycleNumber, asaasResp.id(), saved.getId());

        billingNotificationService.sendPixRenewalEmail(saved, dueDate);
    }

    private LocalDate resolveDueDate(BillingSubscription subscription) {
        Instant periodEnd = subscription.getCurrentPeriodEnd();
        LocalDate today = LocalDate.now();
        if (periodEnd == null) return today;

        LocalDate periodEndDate = periodEnd.atZone(ZoneOffset.UTC).toLocalDate();
        return periodEndDate.isBefore(today) ? today : periodEndDate;
    }

    private AsaasDTO.PaymentResponse createAsaasPixCharge(BillingAccount account, Invoice invoice,
                                                          String externalReference, LocalDate dueDate) {
        try {
            AsaasDTO.CreatePaymentRequest req = new AsaasDTO.CreatePaymentRequest(
                    account.getExternalCustomerId(),
                    AsaasDTO.BillingType.PIX,
                    BigDecimal.valueOf(invoice.getTotalCents(), 2),
                    dueDate,
                    "Renovação mensal - Easy Maintenance",
                    externalReference
            );

            AsaasDTO.PaymentResponse resp = asaasClient.createPayment(req);
            log.info("[PixRenewal] Asaas DETACHED PIX created id={} externalReference={}", resp.id(), externalReference);
            return resp;
        } catch (AsaasException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PixRenewal] Failed to create Asaas PIX detached payment (externalReference={}): {}",
                    externalReference, e.getMessage());
            throw new AsaasException(
                    "Failed to create Asaas PIX detached payment for externalReference=" + externalReference, e);
        }
    }
}
