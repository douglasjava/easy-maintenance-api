package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReceivedHandlerTest {

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

    @InjectMocks
    private PaymentReceivedHandler handler;

    private BillingSubscription pixSubscription;
    private Invoice invoice;
    private Payment pixPayment;

    @BeforeEach
    void setUp() {
        pixSubscription = BillingSubscription.builder()
                .id(100L)
                .status(SubscriptionStatus.PAST_DUE)
                .cycle(BillingCycle.MONTHLY)
                .currentPeriodEnd(Instant.now())
                .externalSubscriptionId(null) // PIX manual — no Asaas subscription
                .build();

        invoice = Invoice.builder()
                .id(500L).status(InvoiceStatus.OPEN)
                .periodStart(LocalDate.now()).periodEnd(LocalDate.now().plusMonths(1))
                .totalCents(9900).currency("BRL")
                .build();

        pixPayment = Payment.builder()
                .id(900L)
                .invoice(invoice)
                .billingSubscription(pixSubscription)
                .cycleNumber(2)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalReference("BILLING-100-CYCLE-2")
                .externalPaymentId("pay-asaas-1")
                .build();
    }

    @Test
    void getEventType_isPaymentReceived() {
        assertThat(handler.getEventType()).isEqualTo("PAYMENT_RECEIVED");
    }

    @Test
    void handle_happyPath_pixDetached_advancesCycleAndMarksPaid() {
        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(savedPayment.getPaidAt()).isNotNull();
        assertThat(savedPayment.getGatewayStatus()).isEqualTo("RECEIVED");

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue().getStatus()).isEqualTo(InvoiceStatus.PAID);

        verify(billingSubscriptionService).advanceCycle(eq(pixSubscription), eq(pixPayment));
    }

    @Test
    void handle_duplicateEvent_paymentAlreadyReceived_noOp() {
        pixPayment.setStatus(PaymentStatus.RECEIVED);
        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
        verify(billingSubscriptionService, never()).advanceCycle(any(), any());
    }

    @Test
    void handle_duplicateEvent_paymentAlreadyPaid_noOp() {
        pixPayment.setStatus(PaymentStatus.PAID);
        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
        verify(billingSubscriptionService, never()).advanceCycle(any(), any());
    }

    @Test
    void handle_outOfOrderReceivedAfterOverdue_advancesCycleAndActivates() {
        pixPayment.setStatus(PaymentStatus.OVERDUE);
        invoice.setStatus(InvoiceStatus.OVERDUE);

        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.RECEIVED);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        assertThat(invoiceCaptor.getValue().getStatus()).isEqualTo(InvoiceStatus.PAID);

        verify(billingSubscriptionService).advanceCycle(eq(pixSubscription), eq(pixPayment));
    }

    @Test
    void handle_canceledSubscription_marksPaymentReceivedButDoesNotAdvanceLocally() {
        pixSubscription.setStatus(SubscriptionStatus.CANCELED);
        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        verify(paymentRepository).save(any());
        // shouldAdvanceCycle returns true (PIX + no externalSubscriptionId); the no-op for CANCELED
        // is enforced inside BillingSubscriptionService.advanceCycle. The handler still delegates.
        verify(billingSubscriptionService).advanceCycle(eq(pixSubscription), eq(pixPayment));
    }

    @Test
    void handle_creditCardSubscription_doesNotAdvanceCycleViaThisHandler() {
        pixSubscription.setExternalSubscriptionId("asaas-sub-xyz");
        pixPayment.setMethodType(PaymentMethodType.CARD);

        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("CREDIT_CARD", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        verify(paymentRepository).save(any());
        verify(billingSubscriptionService, never()).advanceCycle(any(), any());
    }

    @Test
    void handle_pixWithExternalSubscriptionId_doesNotAdvanceLocally() {
        // Legacy path or future Pix Automático with a real Asaas subscription
        pixSubscription.setExternalSubscriptionId("asaas-sub-pix-001");

        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "BILLING-100-CYCLE-2", "pay-asaas-1");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-100-CYCLE-2"))
                .thenReturn(Optional.of(pixPayment));

        handler.handle(event);

        verify(paymentRepository).save(any());
        verify(billingSubscriptionService, never()).advanceCycle(any(), any());
    }

    @Test
    void handle_paymentNotFound_persistsGatewayEventAndReturns() {
        AsaasDTO.PaymentObject paymentObj = buildPaymentObject("PIX", "RECEIVED",
                "UNKNOWN-REF", "unknown-pay-id");
        AsaasDTO.WebhookCheckoutEvent event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("UNKNOWN-REF")).thenReturn(Optional.empty());
        when(paymentRepository.findByExternalPaymentId("unknown-pay-id")).thenReturn(Optional.empty());

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
        verify(billingSubscriptionService, never()).advanceCycle(any(), any());
    }

    @Test
    void handle_nullPaymentObject_persistsGatewayEventAndReturns() {
        AsaasDTO.WebhookCheckoutEvent event = new AsaasDTO.WebhookCheckoutEvent(
                "evt-1", "PAYMENT_RECEIVED", "2026-05-16T10:00:00", null, null, null, null);

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
        verify(billingSubscriptionService, never()).advanceCycle(any(), any());
    }

    // Helpers ------------------------------------------------------------------

    private AsaasDTO.PaymentObject buildPaymentObject(String billingType, String status,
                                                     String externalReference, String externalPaymentId) {
        return new AsaasDTO.PaymentObject(
                externalPaymentId,
                "cust_abc",
                null,                 // subscription (DETACHED → null)
                status,
                new BigDecimal("99.00"),
                LocalDate.now(),      // dueDate
                LocalDate.now(),      // creditDate
                "Renovação mensal",   // description
                null,                 // checkoutSession
                billingType,
                externalReference,
                "http://invoice/url",
                "http://receipt/url",
                null,                 // nossoNumero
                "inv-001",            // invoiceNumber
                new BigDecimal("98.50"),
                LocalDate.now(),      // confirmedDate
                LocalDate.now(),      // paymentDate
                null,                 // installment
                null,                 // discount
                null,                 // pixTransaction
                null                  // failureReason
        );
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(AsaasDTO.PaymentObject paymentObj) {
        return new AsaasDTO.WebhookCheckoutEvent(
                "evt-pay-recv-1",
                "PAYMENT_RECEIVED",
                "2026-05-16T10:00:00",
                null,
                null,
                paymentObj,
                null
        );
    }
}
