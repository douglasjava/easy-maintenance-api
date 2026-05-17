package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.AsaasException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PixRenewalServiceTest {

    @Mock private BillingSubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private AsaasClient asaasClient;
    @Mock private BillingNotificationService billingNotificationService;
    @Mock private PixRenewalService self;

    private PixRenewalService service;

    private User payer;
    private BillingAccount pixAccount;
    private BillingSubscription pixSubscription;
    private Invoice generatedInvoice;

    @BeforeEach
    void setUp() {
        service = new PixRenewalService(
                self,
                subscriptionRepository,
                paymentRepository,
                invoiceService,
                asaasClient,
                billingNotificationService
        );

        payer = User.builder().id(1L).email("payer@test.com").name("Payer Test").build();
        pixAccount = BillingAccount.builder()
                .id(10L)
                .user(payer)
                .billingEmail("billing@test.com")
                .name("Org Test")
                .paymentMethod(PaymentMethodType.PIX)
                .externalCustomerId("cust_pix")
                .build();
        pixSubscription = BillingSubscription.builder()
                .id(100L)
                .billingAccount(pixAccount)
                .status(SubscriptionStatus.ACTIVE)
                .cycle(BillingCycle.MONTHLY)
                .currentPeriodEnd(Instant.now().plus(2, ChronoUnit.DAYS))
                .totalCents(9900L)
                .build();

        BillingPlan plan = BillingPlan.builder()
                .code("BUSINESS").name("Business")
                .billingCycle(BillingCycle.MONTHLY)
                .priceCents(9900)
                .build();
        InvoiceItem item = InvoiceItem.builder()
                .id(1L).plan(plan).description("Business Plan").quantity(1).amountCents(9900).build();
        generatedInvoice = Invoice.builder()
                .id(500L).payer(payer)
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now().plusMonths(1).minusDays(1))
                .dueDate(LocalDate.now().plusDays(5))
                .totalCents(9900)
                .currency("BRL")
                .items(List.of(item))
                .build();
    }

    @Test
    void whenNoEligibleSubscriptions_thenNoOp() {
        when(subscriptionRepository.findPixSubscriptionsDueForRenewal(any()))
                .thenReturn(Collections.emptyList());

        service.processPixRenewals(5);

        verify(self, never()).renewSubscription(anyLong());
        verify(asaasClient, never()).createPayment(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void whenEligibleSubscriptions_thenDelegatesToSelfPerSubscription() {
        BillingSubscription other = BillingSubscription.builder()
                .id(101L).billingAccount(pixAccount)
                .status(SubscriptionStatus.ACTIVE).cycle(BillingCycle.MONTHLY)
                .currentPeriodEnd(Instant.now()).build();

        when(subscriptionRepository.findPixSubscriptionsDueForRenewal(any()))
                .thenReturn(List.of(pixSubscription, other));

        service.processPixRenewals(5);

        verify(self).renewSubscription(100L);
        verify(self).renewSubscription(101L);
    }

    @Test
    void whenOneSubscriptionFails_thenOthersStillProcessed() {
        BillingSubscription other = BillingSubscription.builder()
                .id(101L).billingAccount(pixAccount)
                .status(SubscriptionStatus.ACTIVE).cycle(BillingCycle.MONTHLY)
                .currentPeriodEnd(Instant.now()).build();

        when(subscriptionRepository.findPixSubscriptionsDueForRenewal(any()))
                .thenReturn(List.of(pixSubscription, other));
        org.mockito.Mockito.doThrow(new AsaasException("boom"))
                .when(self).renewSubscription(100L);

        service.processPixRenewals(5);

        verify(self).renewSubscription(100L);
        verify(self).renewSubscription(101L);
    }

    @Test
    void renewSubscription_happyPath_createsDetachedChargeAndPersistsCycle() {
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(pixSubscription));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(100L)).thenReturn(0);
        when(paymentRepository.findByBillingSubscriptionIdAndCycleNumber(100L, 1)).thenReturn(Optional.empty());
        when(invoiceService.generateInvoiceForPayer(eq(1L), any(), any()))
                .thenReturn(Optional.of(generatedInvoice));

        AsaasDTO.PaymentResponse asaasResp = new AsaasDTO.PaymentResponse(
                "pay-renewal-1", "cust_pix", AsaasDTO.BillingType.PIX,
                new BigDecimal("99.00"), LocalDate.now(),
                "PENDING", "http://pay/inv-renewal-1", null);
        when(asaasClient.createPayment(any())).thenReturn(asaasResp);
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(900L);
            return p;
        });

        service.renewSubscription(100L);

        ArgumentCaptor<AsaasDTO.CreatePaymentRequest> reqCaptor =
                ArgumentCaptor.forClass(AsaasDTO.CreatePaymentRequest.class);
        verify(asaasClient).createPayment(reqCaptor.capture());
        AsaasDTO.CreatePaymentRequest sent = reqCaptor.getValue();
        assertThat(sent.billingType()).isEqualTo(AsaasDTO.BillingType.PIX);
        assertThat(sent.customer()).isEqualTo("cust_pix");
        assertThat(sent.externalReference()).isEqualTo("BILLING-100-CYCLE-1");
        assertThat(sent.value()).isEqualByComparingTo(new BigDecimal("99.00"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getCycleNumber()).isEqualTo(1);
        assertThat(saved.getBillingSubscription().getId()).isEqualTo(100L);
        assertThat(saved.getExternalPaymentId()).isEqualTo("pay-renewal-1");
        assertThat(saved.getExternalReference()).isEqualTo("BILLING-100-CYCLE-1");
        assertThat(saved.getMethodType()).isEqualTo(PaymentMethodType.PIX);

        verify(billingNotificationService).sendPixRenewalEmail(eq(saved), any(LocalDate.class));
    }

    @Test
    void renewSubscription_whenCycleAlreadyExists_thenSkipsAndDoesNotCallAsaas() {
        Payment existing = Payment.builder().id(800L).cycleNumber(3).build();
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(pixSubscription));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(100L)).thenReturn(2);
        when(paymentRepository.findByBillingSubscriptionIdAndCycleNumber(100L, 3)).thenReturn(Optional.of(existing));

        service.renewSubscription(100L);

        verify(invoiceService, never()).generateInvoiceForPayer(anyLong(), any(), any());
        verify(asaasClient, never()).createPayment(any());
        verify(paymentRepository, never()).save(any());
        verify(billingNotificationService, never()).sendPixRenewalEmail(any(), any());
    }

    @Test
    void renewSubscription_whenAsaasFails_thenNoPaymentSavedAndNoEmail() {
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(pixSubscription));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(100L)).thenReturn(0);
        when(paymentRepository.findByBillingSubscriptionIdAndCycleNumber(100L, 1)).thenReturn(Optional.empty());
        when(invoiceService.generateInvoiceForPayer(eq(1L), any(), any()))
                .thenReturn(Optional.of(generatedInvoice));
        when(asaasClient.createPayment(any())).thenThrow(new AsaasException("Asaas down"));

        try {
            service.renewSubscription(100L);
        } catch (AsaasException expected) {
            // expected — outer loop catches and continues
        }

        verify(paymentRepository, never()).save(any());
        verify(billingNotificationService, never()).sendPixRenewalEmail(any(), any());
    }

    @Test
    void renewSubscription_whenInvoiceCannotBeGenerated_thenSkipsAsaas() {
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(pixSubscription));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(100L)).thenReturn(0);
        when(paymentRepository.findByBillingSubscriptionIdAndCycleNumber(100L, 1)).thenReturn(Optional.empty());
        when(invoiceService.generateInvoiceForPayer(eq(1L), any(), any()))
                .thenReturn(Optional.empty());

        service.renewSubscription(100L);

        verify(asaasClient, never()).createPayment(any());
        verify(paymentRepository, never()).save(any());
        verify(billingNotificationService, never()).sendPixRenewalEmail(any(), any());
    }

    @Test
    void renewSubscription_incrementsCycleNumberBasedOnExistingMax() {
        when(subscriptionRepository.findById(100L)).thenReturn(Optional.of(pixSubscription));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(100L)).thenReturn(7);
        when(paymentRepository.findByBillingSubscriptionIdAndCycleNumber(100L, 8)).thenReturn(Optional.empty());
        when(invoiceService.generateInvoiceForPayer(eq(1L), any(), any()))
                .thenReturn(Optional.of(generatedInvoice));

        AsaasDTO.PaymentResponse asaasResp = new AsaasDTO.PaymentResponse(
                "pay-renewal-8", "cust_pix", AsaasDTO.BillingType.PIX,
                new BigDecimal("99.00"), LocalDate.now(),
                "PENDING", "http://pay/inv-8", null);
        when(asaasClient.createPayment(any())).thenReturn(asaasResp);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.renewSubscription(100L);

        ArgumentCaptor<AsaasDTO.CreatePaymentRequest> reqCaptor =
                ArgumentCaptor.forClass(AsaasDTO.CreatePaymentRequest.class);
        verify(asaasClient).createPayment(reqCaptor.capture());
        assertThat(reqCaptor.getValue().externalReference()).isEqualTo("BILLING-100-CYCLE-8");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(1)).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getCycleNumber()).isEqualTo(8);
    }
}
