package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.affiliates.application.service.CommissionService;
import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.AffiliateStatus;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.AffiliateRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentGatewayEventRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReceivedHandlerCommissionTest {

    @Mock private InvoiceService invoiceService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGatewayEventRepository paymentGatewayEventRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private BillingAccountRepository billingAccountRepository;
    @Mock private BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock private BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private BillingSubscriptionService billingSubscriptionService;
    @Mock private CommissionService commissionService;
    @Mock private AffiliateRepository affiliateRepository;

    @InjectMocks private PaymentReceivedHandler handler;

    private BillingSubscription subscription;

    @BeforeEach
    void setUp() {
        subscription = BillingSubscription.builder()
                .id(10L).status(SubscriptionStatus.ACTIVE)
                .cycle(BillingCycle.MONTHLY).currentPeriodEnd(Instant.now())
                .externalSubscriptionId(null).build();
    }

    // ── first payment with referralCode → creates commission ─────────────

    @Test
    void handle_firstPayment_withReferralCode_createsCommission() {
        Payment payment = payment(1, 29900);

        when(paymentRepository.findByExternalReference("ref-1")).thenReturn(Optional.of(payment));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(10L))
                .thenReturn(List.of(orgItemWithPlan("ORG001", "PRO")));

        Organization org = org("ORG001", "ABC123", 5L);
        when(organizationRepository.findByCode("ORG001")).thenReturn(Optional.of(org));

        Affiliate affiliate = affiliate("ABC123");
        when(affiliateRepository.findByCode("ABC123")).thenReturn(Optional.of(affiliate));

        handler.handle(buildEvent("ref-1", "pay-1"));

        verify(commissionService).createCommission(
                eq(affiliate), eq(5L), eq("PRO"), eq(new BigDecimal("299.00")));
    }

    // ── second payment → no commission ───────────────────────────────────

    @Test
    void handle_secondPayment_doesNotTriggerCommission() {
        Payment payment = payment(2, 29900);
        when(paymentRepository.findByExternalReference("ref-2")).thenReturn(Optional.of(payment));

        handler.handle(buildEvent("ref-2", "pay-2"));

        verifyNoInteractions(commissionService, affiliateRepository);
    }

    // ── first payment but affiliate INACTIVE ─────────────────────────────

    @Test
    void handle_firstPayment_inactiveAffiliate_doesNotCreateCommission() {
        Payment payment = payment(1, 29900);
        when(paymentRepository.findByExternalReference("ref-3")).thenReturn(Optional.of(payment));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(10L))
                .thenReturn(List.of(orgItem("ORG001")));

        Organization org = org("ORG001", "ABC123", 5L);
        when(organizationRepository.findByCode("ORG001")).thenReturn(Optional.of(org));

        Affiliate inactive = affiliate("ABC123");
        inactive.setStatus(AffiliateStatus.INACTIVE);
        when(affiliateRepository.findByCode("ABC123")).thenReturn(Optional.of(inactive));

        handler.handle(buildEvent("ref-3", "pay-3"));

        verify(commissionService, never()).createCommission(any(), any(), any(), any());
    }

    // ── first payment but org has no referralCode ─────────────────────────

    @Test
    void handle_firstPayment_noReferralCode_doesNotCreateCommission() {
        Payment payment = payment(1, 29900);
        when(paymentRepository.findByExternalReference("ref-4")).thenReturn(Optional.of(payment));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(10L))
                .thenReturn(List.of(orgItem("ORG001")));

        Organization org = org("ORG001", null, 5L); // no referralCode
        when(organizationRepository.findByCode("ORG001")).thenReturn(Optional.of(org));

        handler.handle(buildEvent("ref-4", "pay-4"));

        verify(commissionService, never()).createCommission(any(), any(), any(), any());
        verifyNoInteractions(affiliateRepository);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Payment payment(int cycleNumber, int amountCents) {
        return Payment.builder()
                .id(100L + cycleNumber)
                .billingSubscription(subscription)
                .cycleNumber(cycleNumber)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(amountCents)
                .currency("BRL")
                .externalReference("ref-" + cycleNumber)
                .externalPaymentId("pay-" + cycleNumber)
                .build();
    }

    private BillingSubscriptionItem orgItem(String orgCode) {
        BillingSubscriptionItem item = new BillingSubscriptionItem();
        item.setSourceType(BillingSubscriptionItemSourceType.ORGANIZATION);
        item.setSourceId(orgCode);
        return item;
    }

    private BillingSubscriptionItem orgItemWithPlan(String orgCode, String planCode) {
        BillingSubscriptionItem item = new BillingSubscriptionItem();
        item.setSourceType(BillingSubscriptionItemSourceType.ORGANIZATION);
        item.setSourceId(orgCode);
        BillingPlan plan = new BillingPlan();
        plan.setCode(planCode);
        item.setPlan(plan);
        return item;
    }

    private Organization org(String code, String referralCode, Long id) {
        Organization org = new Organization();
        org.setId(id);
        org.setCode(code);
        org.setReferralCode(referralCode);
        return org;
    }

    private Affiliate affiliate(String code) {
        return Affiliate.builder()
                .id(1L).code(code).name("Ana").email("ana@test.com")
                .whatsapp("31999").commissionRate(new BigDecimal("0.20"))
                .status(AffiliateStatus.ACTIVE).build();
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String externalRef, String paymentId) {
        AsaasDTO.PaymentObject paymentObj = new AsaasDTO.PaymentObject(
                paymentId, "cust-1", null, "RECEIVED",
                new BigDecimal("299.00"), LocalDate.now(), LocalDate.now(),
                "Plan payment", null, "PIX", externalRef,
                null, null, null, null,
                new BigDecimal("299.00"), LocalDate.now(), LocalDate.now(),
                null, null, null, null);
        return new AsaasDTO.WebhookCheckoutEvent(
                "evt-1", "PAYMENT_RECEIVED", "2026-06-21T10:00:00",
                null, null, paymentObj, null);
    }
}
