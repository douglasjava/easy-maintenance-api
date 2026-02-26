package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.AsaasException;
import com.brainbyte.easy_maintenance.infrastructure.mail.MailService;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrialExpirationJobService {

    private final InvoiceService invoiceService;
    private final BillingAccountRepository billingAccountRepository;
    private final PaymentRepository paymentRepository;
    private final AsaasClient asaasClient;
    private final AsaasProperties asaasProperties;
    private final MailService mailerSendService;
    private final EmailTemplateHelper emailTemplateHelper;

    @Transactional
    public void processTrialsExpiringWithinDays(int daysAhead) {

        Instant threshold = Instant.now().plusSeconds(daysAhead * 24L * 3600L);
        log.info("Processing trials with trialEndsAt <= {} ({} days ahead)", threshold, daysAhead);

        LocalDate periodStart = LocalDate.now();
        
        LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

        List<Invoice> invoices = invoiceService.generateInvoices(
                periodStart, 
                periodEnd, 
                List.of(SubscriptionStatus.TRIAL), 
                threshold
        );

        invoices.forEach(this::handleGeneratedInvoice);

    }

    private void handleGeneratedInvoice(Invoice invoice) {

        User payer = invoice.getPayer();
        if (invoice.getItems().isEmpty()) return;

        LocalDate nextDueDate = invoice.getPeriodStart();

        var firstItem = invoice.getItems().getFirst();
        BillingPlan plan = firstItem.getPlan();

        createProviderSubscriptionAndPayment(payer, plan, invoice, nextDueDate);

    }

    private void createProviderSubscriptionAndPayment(User payer, BillingPlan plan, Invoice invoice, LocalDate nextDueDate) {
        log.info("Create Subscription And Payment for Provider {}, Plan {} - Date {}", payer, plan, nextDueDate);

        var accountOpt = billingAccountRepository.findByUserId(payer.getId());
        if (accountOpt.isEmpty()) {
            log.warn("BillingAccount not found for payer {}. Skipping Asaas creation and email.", payer.getId());
            return;
        }
        var account = accountOpt.get();
        if (account.getExternalCustomerId() == null || account.getExternalCustomerId().isBlank()) {
            log.warn("externalCustomerId not set for payer {}. Skipping Asaas creation and email.", payer.getId());
            return;
        }

        var asaasResponse = createAsaasCheckout(account, invoice, plan, nextDueDate);

        var paymentLink = asaasResponse.link();
        var externalPaymentId = asaasResponse.id();

        var payment = createAndSavePayment(payer, invoice, account, externalPaymentId, paymentLink);

        sendTrialExpirationEmail(payer, account, payment.getPaymentLink(), invoice);
    }

    private AsaasDTO.CheckoutResponse createAsaasCheckout(BillingAccount account, Invoice invoice, BillingPlan plan, LocalDate nextDueDate) {
        try {
            AsaasDTO.CreateCheckoutRequest req = mapToAsaasCheckoutRequest(account, invoice, plan, nextDueDate);

            AsaasDTO.CheckoutResponse resp = asaasClient.createCheckout(req);
            log.info("Asaas checkout created id={} for payer {}", resp.id(), account.getUser().getId());
            return resp;
        } catch (Exception e) {
            log.error("Failed to create Asaas checkout for payer {}: {}", account.getUser().getId(), e.getMessage());
            throw new AsaasException(String.format("Failed to create Asaas checkout for payer %s", account.getUser().getId()), e);
        }
    }

    private AsaasDTO.CreateCheckoutRequest mapToAsaasCheckoutRequest(BillingAccount account, Invoice invoice, BillingPlan plan, LocalDate nextDueDate) {
        AsaasDTO.Cycle cycle = mapCycle(plan.getBillingCycle());

        var items = invoice.getItems().stream()
                .map(invoiceItem -> new AsaasDTO.CheckoutItem(
                        "INVITEM-" + invoiceItem.getId() + "-" + UUID.randomUUID(),
                        invoiceItem.getDescription(),
                        invoiceItem.getPlan() != null ? invoiceItem.getPlan().getCode() : "EASY MAINTENANCE",
                        invoiceItem.getQuantity(),
                        BigDecimal.valueOf(invoiceItem.getAmountCents(), 2)
                ))
                .toList();

        var subscription = new AsaasDTO.CheckoutSubscription(
                cycle,
                nextDueDate.toString(),
                nextDueDate.toString()
        );

        int minutesToExpire = asaasProperties.checkoutMinutesToExpire() != null ? asaasProperties.checkoutMinutesToExpire() : 120;

        var callback = new AsaasDTO.CheckoutCallback(
                asaasProperties.checkoutSuccessUrl(),
                asaasProperties.checkoutCancelUrl(),
                asaasProperties.checkoutExpiredUrl()
        );

        return new AsaasDTO.CreateCheckoutRequest(
                List.of(mapBillingType(account.getPaymentMethod())),
                List.of(AsaasDTO.ChargeTypes.RECURRENT),
                minutesToExpire,
                "INV-" + invoice.getId() + "-" + UUID.randomUUID(),
                callback,
                items,
                subscription,
                account.getExternalCustomerId()
        );
    }

    private Payment createAndSavePayment(User payer, Invoice invoice, BillingAccount account, String externalPaymentId, String paymentLink) {
        Payment payment = Payment.builder()
                .invoice(invoice)
                .payer(payer)
                .provider(PaymentProvider.ASAAS)
                .methodType(account.getPaymentMethod() != null ? account.getPaymentMethod() : PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .externalReference("INV-" + invoice.getId())
                .externalPaymentId(externalPaymentId)
                .paymentLink(paymentLink)
                .build();
        return paymentRepository.save(payment);
    }

    private void sendTrialExpirationEmail(User payer, BillingAccount account, String paymentLink, Invoice invoice) {
        try {
            String toEmail = account.getBillingEmail() != null ? account.getBillingEmail() : payer.getEmail();
            String toName = account.getName() != null ? account.getName() : payer.getName();
            String subject = "Renove sua assinatura - Easy Maintenance";
            String html = emailTemplateHelper.generateSubscriptionExpirationHtml(payer.getName(), paymentLink, invoice.getDueDate().toString());
            mailerSendService.sendEmail(toEmail, toName, subject, html, html);
        } catch (Exception e) {
            log.error("Failed to send email for invoice {}: {}", invoice.getId(), e.getMessage());
        }
    }

    private static AsaasDTO.Cycle mapCycle(BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? AsaasDTO.Cycle.YEARLY : AsaasDTO.Cycle.MONTHLY;
    }

    private static AsaasDTO.BillingType mapBillingType(PaymentMethodType method) {
        if (method == PaymentMethodType.BOLETO) return AsaasDTO.BillingType.BOLETO;
        if (method == PaymentMethodType.CARD) return AsaasDTO.BillingType.CREDIT_CARD;
        return AsaasDTO.BillingType.PIX;
    }
    
}
