package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingReconciliationServiceTest {

    @Mock private BillingSubscriptionRepository subscriptionRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AsaasClient asaasClient;

    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private BillingReconciliationService service;

    private BillingSubscription activeSub;
    private BillingSubscription pixSub;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "windowDays", 90);

        activeSub = BillingSubscription.builder()
                .id(1L)
                .status(SubscriptionStatus.ACTIVE)
                .externalSubscriptionId("asaas-sub-cc-001")
                .build();

        pixSub = BillingSubscription.builder()
                .id(2L)
                .status(SubscriptionStatus.ACTIVE)
                .externalSubscriptionId(null) // PIX manual — no Asaas sub
                .build();
    }

    // -------------------------------------------------------------------------
    // reconcile() — dispatch
    // -------------------------------------------------------------------------

    @Test
    void reconcile_noCandidates_noInteractionWithAsaas() {
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of());

        service.reconcile();

        verifyNoInteractions(asaasClient, paymentRepository);
    }

    @Test
    void reconcile_ccSubActiveAsaasInactive_cancelsSubscription() {
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(activeSub));
        when(asaasClient.getSubscription("asaas-sub-cc-001"))
                .thenReturn(buildSubResponse("INACTIVE"));

        service.reconcile();

        ArgumentCaptor<BillingSubscription> captor = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        double count = meterRegistry.counter(BillingReconciliationService.METRIC_NAME, "type", "subscription_canceled").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void reconcile_ccSubActiveAsaasActive_noop() {
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(activeSub));
        when(asaasClient.getSubscription("asaas-sub-cc-001"))
                .thenReturn(buildSubResponse("ACTIVE"));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(1L, PaymentStatus.PENDING))
                .thenReturn(List.of());

        service.reconcile();

        verify(subscriptionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // checkAndFixPendingPayments()
    // -------------------------------------------------------------------------

    @Test
    void pendingPayment_asaasReceived_marksLocalReceived() {
        Payment pending = buildPayment(1L, PaymentStatus.PENDING, "asaas-pay-001");
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(pixSub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(2L, PaymentStatus.PENDING))
                .thenReturn(List.of(pending));
        when(asaasClient.getPayment("asaas-pay-001")).thenReturn(buildPaymentResponse("RECEIVED"));

        service.reconcile();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.RECEIVED);

        double count = meterRegistry.counter(BillingReconciliationService.METRIC_NAME, "type", "payment_received").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void pendingPayment_asaasConfirmed_marksLocalReceived() {
        Payment pending = buildPayment(2L, PaymentStatus.PENDING, "asaas-pay-002");
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(pixSub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(2L, PaymentStatus.PENDING))
                .thenReturn(List.of(pending));
        when(asaasClient.getPayment("asaas-pay-002")).thenReturn(buildPaymentResponse("CONFIRMED"));

        service.reconcile();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.RECEIVED);
    }

    @Test
    void pendingPayment_asaasPending_noop() {
        Payment pending = buildPayment(3L, PaymentStatus.PENDING, "asaas-pay-003");
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(pixSub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(2L, PaymentStatus.PENDING))
                .thenReturn(List.of(pending));
        when(asaasClient.getPayment("asaas-pay-003")).thenReturn(buildPaymentResponse("PENDING"));

        service.reconcile();

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pendingPayment_noExternalId_skipped() {
        Payment noExternalId = buildPayment(4L, PaymentStatus.PENDING, null);
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(pixSub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(2L, PaymentStatus.PENDING))
                .thenReturn(List.of(noExternalId));

        service.reconcile();

        verifyNoInteractions(asaasClient);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void pendingPayment_asaasThrows_logsErrorAndContinues() {
        Payment p1 = buildPayment(5L, PaymentStatus.PENDING, "asaas-pay-fail");
        Payment p2 = buildPayment(6L, PaymentStatus.PENDING, "asaas-pay-ok");
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(pixSub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(2L, PaymentStatus.PENDING))
                .thenReturn(List.of(p1, p2));
        when(asaasClient.getPayment("asaas-pay-fail")).thenThrow(new RuntimeException("timeout"));
        when(asaasClient.getPayment("asaas-pay-ok")).thenReturn(buildPaymentResponse("RECEIVED"));

        service.reconcile();

        // p2 still fixed despite p1 failure
        verify(paymentRepository, times(1)).save(any());
    }

    @Test
    void ccSubCanceledInAsaas_paymentCheckSkipped() {
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(activeSub));
        when(asaasClient.getSubscription("asaas-sub-cc-001")).thenReturn(buildSubResponse("INACTIVE"));

        service.reconcile();

        // subscription was canceled → payment check skipped entirely
        verify(paymentRepository, never()).findByBillingSubscriptionIdAndStatus(any(), any());
    }

    @Test
    void pixSub_noExternalSubId_subscriptionCheckSkipped() {
        when(subscriptionRepository.findReconciliationCandidates(any(), any())).thenReturn(List.of(pixSub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(2L, PaymentStatus.PENDING))
                .thenReturn(List.of());

        service.reconcile();

        verify(asaasClient, never()).getSubscription(any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Payment buildPayment(Long id, PaymentStatus status, String externalPaymentId) {
        return Payment.builder()
                .id(id)
                .billingSubscription(pixSub)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(status)
                .amountCents(9900)
                .currency("BRL")
                .externalPaymentId(externalPaymentId)
                .build();
    }

    private AsaasDTO.PaymentResponse buildPaymentResponse(String status) {
        return new AsaasDTO.PaymentResponse(
                "asaas-pay-001", "cust-001",
                AsaasDTO.BillingType.PIX,
                BigDecimal.valueOf(99.00),
                LocalDate.now(),
                status, null, null
        );
    }

    private AsaasDTO.SubscriptionResponse buildSubResponse(String status) {
        return new AsaasDTO.SubscriptionResponse(
                "asaas-sub-cc-001", "cust-001",
                AsaasDTO.BillingType.CREDIT_CARD,
                BigDecimal.valueOf(99.00),
                LocalDate.now(),
                AsaasDTO.Cycle.MONTHLY,
                "Plano Business",
                status
        );
    }
}
