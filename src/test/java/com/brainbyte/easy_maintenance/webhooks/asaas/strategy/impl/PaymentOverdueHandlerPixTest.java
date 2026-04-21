package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for TASK-047: PIX overdue email notification in PaymentOverdueHandler.
 */
@ExtendWith(MockitoExtension.class)
class PaymentOverdueHandlerPixTest {

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
    @Mock private BillingNotificationService billingNotificationService;

    @InjectMocks
    private PaymentOverdueHandler handler;

    private Payment pixPayment;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        invoice = mock(Invoice.class);

        pixPayment = Payment.builder()
                .id(1L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalPaymentId("pay-pix-001")
                .externalReference("BILLING-42")
                .build();
        pixPayment.setInvoice(invoice);
        pixPayment.setPayer(mock(User.class));
    }

    // -----------------------------------------------------------------------
    // PIX — happy path: OVERDUE event triggers email
    // -----------------------------------------------------------------------

    @Test
    void handle_pixOverdue_shouldMarkOverdueAndSendEmail() {
        var event = buildEvent("pay-pix-001");

        when(paymentRepository.findByExternalPaymentId("pay-pix-001")).thenReturn(Optional.of(pixPayment));
        when(paymentRepository.save(any())).thenReturn(pixPayment);

        handler.handle(event);

        verify(paymentRepository).save(any());
        verify(billingNotificationService).sendPixOverdueEmail(pixPayment);
    }

    // -----------------------------------------------------------------------
    // CARD — overdue event must NOT send PIX email
    // -----------------------------------------------------------------------

    @Test
    void handle_creditCardOverdue_doesNotSendPixEmail() {
        Payment cardPayment = Payment.builder()
                .id(2L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalPaymentId("pay-card-001")
                .externalReference("BILLING-43")
                .build();
        cardPayment.setInvoice(invoice);
        cardPayment.setPayer(mock(User.class));

        var event = buildEvent("pay-card-001");

        when(paymentRepository.findByExternalPaymentId("pay-card-001")).thenReturn(Optional.of(cardPayment));
        when(paymentRepository.save(any())).thenReturn(cardPayment);

        handler.handle(event);

        verify(billingNotificationService, never()).sendPixOverdueEmail(any());
    }

    // -----------------------------------------------------------------------
    // Final state — payment already final, skip everything
    // -----------------------------------------------------------------------

    @Test
    void handle_finalStatePayment_skipsUpdateAndEmail() {
        Payment paidPayment = Payment.builder()
                .id(3L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PAID)
                .externalPaymentId("pay-pix-final")
                .build();

        var event = buildEvent("pay-pix-final");

        when(paymentRepository.findByExternalPaymentId("pay-pix-final")).thenReturn(Optional.of(paidPayment));

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
        verify(billingNotificationService, never()).sendPixOverdueEmail(any());
    }

    // -----------------------------------------------------------------------
    // Payment not found in DB — skip all processing
    // -----------------------------------------------------------------------

    @Test
    void handle_paymentNotFound_skipsEmailNotification() {
        var event = buildEvent("pay-unknown-001");

        when(paymentRepository.findByExternalPaymentId("pay-unknown-001")).thenReturn(Optional.empty());

        handler.handle(event);

        verify(billingNotificationService, never()).sendPixOverdueEmail(any());
    }

    // -----------------------------------------------------------------------
    // Null payment object — guard clause
    // -----------------------------------------------------------------------

    @Test
    void handle_nullPaymentObject_doesNotThrowAndSkipsEmail() {
        var event = new AsaasDTO.WebhookCheckoutEvent(
                "evt-null", "PAYMENT_OVERDUE", "2026-05-01T10:00:00",
                null, null, null, null
        );

        handler.handle(event);

        verify(billingNotificationService, never()).sendPixOverdueEmail(any());
    }

    // -----------------------------------------------------------------------
    // Payer null — handler must not throw; email notification still sent
    // -----------------------------------------------------------------------

    @Test
    void handle_pixOverdue_payerIsNull_doesNotThrowAndEmailStillSent() {
        Payment paymentWithNullPayer = Payment.builder()
                .id(4L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalPaymentId("pay-pix-nopayer")
                .build();
        paymentWithNullPayer.setInvoice(invoice);
        // payer intentionally left null

        var event = buildEvent("pay-pix-nopayer");

        when(paymentRepository.findByExternalPaymentId("pay-pix-nopayer")).thenReturn(Optional.of(paymentWithNullPayer));
        when(paymentRepository.save(any())).thenReturn(paymentWithNullPayer);

        handler.handle(event);

        verify(paymentRepository).save(any());
        verify(billingNotificationService).sendPixOverdueEmail(paymentWithNullPayer);
    }

    // -----------------------------------------------------------------------
    // paymentLink null — email is still sent without link
    // -----------------------------------------------------------------------

    @Test
    void handle_pixOverdue_paymentLinkNull_emailStillSent() {
        // paymentLink not set on pixPayment (null by default from builder)
        var event = buildEvent("pay-pix-001");

        when(paymentRepository.findByExternalPaymentId("pay-pix-001")).thenReturn(Optional.of(pixPayment));
        when(paymentRepository.save(any())).thenReturn(pixPayment);

        handler.handle(event);

        verify(billingNotificationService).sendPixOverdueEmail(pixPayment);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String externalPaymentId) {
        var paymentObj = new AsaasDTO.PaymentObject(
                externalPaymentId, "cust-001", "sub-001", "OVERDUE",
                BigDecimal.valueOf(99.00), LocalDate.of(2026, 5, 11),
                null, null, null, "PIX", "BILLING-42",
                null, null, null, null, null, null, null, null, null, null
        );
        return new AsaasDTO.WebhookCheckoutEvent(
                "evt-001", "PAYMENT_OVERDUE", "2026-05-01T10:00:00",
                null, null, paymentObj, null
        );
    }
}
