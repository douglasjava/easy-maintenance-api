package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.error.RefusalBucket;
import com.brainbyte.easy_maintenance.billing.error.RefusalReasonClassifier;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentRefusedHandlerTest {

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
    @Mock private RefusalReasonClassifier classifier;
    @Mock private BillingNotificationService billingNotificationService;
    @Mock private BillingSubscriptionService billingSubscriptionService;

    @InjectMocks
    private PaymentRefusedHandler handler;

    private BillingSubscription subscription;
    private Payment payment;

    @BeforeEach
    void setUp() {
        subscription = BillingSubscription.builder()
                .id(10L)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        payment = Payment.builder()
                .id(1L)
                .billingSubscription(subscription)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalPaymentId("pay-ref-1")
                .externalReference("BILLING-10-CYCLE-1")
                .build();
    }

    // -------------------------------------------------------------------------
    // Routing: TRANSIENT
    // -------------------------------------------------------------------------

    @Test
    void handle_transient_marksFailedNoSubscriptionChange() {
        when(classifier.classify("TIMEOUT")).thenReturn(RefusalBucket.TRANSIENT);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("TIMEOUT"));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("TIMEOUT");

        verify(billingSubscriptionRepository, never()).save(any());
        verify(billingNotificationService, never()).sendSubscriptionBlockedEmail(any());
        verify(billingNotificationService, never()).sendCancellationProcessedEmail(any());
    }

    // -------------------------------------------------------------------------
    // Routing: USER_ACTION
    // -------------------------------------------------------------------------

    @Test
    void handle_userAction_movesToPastDueAndNotifies() {
        when(classifier.classify("INSUFFICIENT_FUNDS")).thenReturn(RefusalBucket.USER_ACTION);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("INSUFFICIENT_FUNDS"));

        verify(paymentRepository).save(any());

        ArgumentCaptor<BillingSubscription> subCaptor = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(billingSubscriptionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        verify(billingNotificationService).sendSubscriptionBlockedEmail(subscription);
    }

    @Test
    void handle_userAction_alreadyPastDue_doesNotSaveAgain() {
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        when(classifier.classify("EXPIRED_CARD")).thenReturn(RefusalBucket.USER_ACTION);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("EXPIRED_CARD"));

        verify(billingSubscriptionRepository, never()).save(any());
        verify(billingNotificationService, never()).sendSubscriptionBlockedEmail(any());
    }

    // -------------------------------------------------------------------------
    // Routing: HARD_FAIL
    // -------------------------------------------------------------------------

    @Test
    void handle_hardFail_cancelesSubscriptionAndNotifies() {
        when(classifier.classify("FRAUD_DETECTED")).thenReturn(RefusalBucket.HARD_FAIL);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("FRAUD_DETECTED"));

        verify(paymentRepository).save(any());

        ArgumentCaptor<BillingSubscription> subCaptor = ArgumentCaptor.forClass(BillingSubscription.class);
        verify(billingSubscriptionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(billingNotificationService).sendCancellationProcessedEmail(subscription);
    }

    @Test
    void handle_hardFail_alreadyCanceled_doesNotSaveAgain() {
        subscription.setStatus(SubscriptionStatus.CANCELED);
        when(classifier.classify("CARD_STOLEN")).thenReturn(RefusalBucket.HARD_FAIL);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("CARD_STOLEN"));

        verify(billingSubscriptionRepository, never()).save(any());
        verify(billingNotificationService, never()).sendCancellationProcessedEmail(any());
    }

    // -------------------------------------------------------------------------
    // Routing: INFO / UNKNOWN
    // -------------------------------------------------------------------------

    @Test
    void handle_info_onlyLogsNoStateChange() {
        when(classifier.classify("CHARGEBACK")).thenReturn(RefusalBucket.INFO);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("CHARGEBACK"));

        verify(paymentRepository).save(any());
        verify(billingSubscriptionRepository, never()).save(any());
        verify(billingNotificationService, never()).sendSubscriptionBlockedEmail(any());
        verify(billingNotificationService, never()).sendCancellationProcessedEmail(any());
    }

    @Test
    void handle_unknown_onlyLogsNoStateChange() {
        when(classifier.classify(null)).thenReturn(RefusalBucket.UNKNOWN);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent(null));

        verify(paymentRepository).save(any());
        verify(billingSubscriptionRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Guard: already final payment
    // -------------------------------------------------------------------------

    @Test
    void handle_paymentAlreadyFinal_noOp() {
        payment.setStatus(PaymentStatus.CANCELED);
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1"))
                .thenReturn(Optional.of(payment));

        handler.handle(buildEvent("TIMEOUT"));

        verify(paymentRepository, never()).save(any());
        verify(classifier, never()).classify(any());
    }

    // -------------------------------------------------------------------------
    // Guard: payment not found
    // -------------------------------------------------------------------------

    @Test
    void handle_paymentNotFound_noStateChange() {
        when(paymentRepository.findByExternalReference("BILLING-10-CYCLE-1")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalPaymentId("pay-ref-1")).thenReturn(Optional.empty());

        handler.handle(buildEvent("TIMEOUT"));

        verify(paymentRepository, never()).save(any());
        verify(classifier, never()).classify(any());
    }

    // -------------------------------------------------------------------------
    // Guard: null payment object
    // -------------------------------------------------------------------------

    @Test
    void handle_nullPaymentObject_noOp() {
        var event = new AsaasDTO.WebhookCheckoutEvent(
                "evt-1", "PAYMENT_REFUSED", "2026-05-16T10:00:00", null, null, null, null);

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // getEventType
    // -------------------------------------------------------------------------

    @Test
    void getEventType_isPaymentRefused() {
        assertThat(handler.getEventType()).isEqualTo("PAYMENT_REFUSED");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String failureCode) {
        var paymentObj = new AsaasDTO.PaymentObject(
                "pay-ref-1", "cust-001", null, "REFUSED",
                new BigDecimal("99.00"), LocalDate.now(),
                null, null, null, "CREDIT_CARD", "BILLING-10-CYCLE-1",
                null, null, null, null, null, null, null, null, null, null,
                failureCode
        );
        return new AsaasDTO.WebhookCheckoutEvent(
                "evt-refused-1", "PAYMENT_REFUSED", "2026-05-16T10:00:00",
                null, null, paymentObj, null
        );
    }
}
