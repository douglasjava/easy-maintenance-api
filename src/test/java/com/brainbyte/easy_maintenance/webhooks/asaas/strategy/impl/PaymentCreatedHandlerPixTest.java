package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
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
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for TASK-046: PIX field population in PaymentCreatedHandler.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCreatedHandlerPixTest {

    @Mock private InvoiceService invoiceService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGatewayEventRepository paymentGatewayEventRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private BillingAccountRepository billingAccountRepository;
    @Mock private BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock private BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks
    private PaymentCreatedHandler handler;

    private Payment existingPayment;

    @BeforeEach
    void setUp() {
        existingPayment = Payment.builder()
                .id(1L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalReference("BILLING-42")
                .build();
    }

    // -----------------------------------------------------------------------
    // PIX — happy path with full pixTransaction data
    // -----------------------------------------------------------------------

    @Test
    void handle_pixPayment_shouldPopulateAllPixFields() {
        var pixQrCode = new AsaasDTO.PixQrCode(
                "base64ImageData==",
                "00020126580014br.gov.bcb.pix...",
                LocalDateTime.of(2026, 5, 11, 23, 59, 59)
        );
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-001", pixQrCode);
        var paymentObj = buildPaymentObject("PIX", "PENDING", pixTransaction);
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getPixQrCode()).isEqualTo("00020126580014br.gov.bcb.pix...");
        assertThat(saved.getPixQrCodeBase64()).isEqualTo("base64ImageData==");
        assertThat(saved.getPixExpiresAt()).isNotNull();
    }

    @Test
    void handle_pixPayment_expiresAtSetFromExpirationDate() {
        var expirationDate = LocalDateTime.of(2026, 5, 15, 23, 59, 59);
        var pixQrCode = new AsaasDTO.PixQrCode("base64==", "payload...", expirationDate);
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-002", pixQrCode);
        var paymentObj = buildPaymentObject("PIX", "PENDING", pixTransaction);
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPixExpiresAt()).isNotNull();
    }

    @Test
    void handle_pixPayment_noExpirationDate_fallsBackToDueDate() {
        // expirationDate is null — handler should use dueDate as fallback
        var pixQrCode = new AsaasDTO.PixQrCode("base64==", "payload...", null);
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-003", pixQrCode);
        var paymentObj = new AsaasDTO.PaymentObject(
                "pay-001", "cust-001", "sub-001", "PENDING",
                BigDecimal.valueOf(99.00), LocalDate.of(2026, 5, 11),
                null, null, null, "PIX", "BILLING-42",
                null, null, null, null, null, null, null, null, null, pixTransaction
        );
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        // pixExpiresAt should be set (fallback from dueDate)
        assertThat(captor.getValue().getPixExpiresAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // PIX — no pixTransaction in webhook (older API versions / edge case)
    // -----------------------------------------------------------------------

    @Test
    void handle_pixPayment_noPixTransaction_doesNotThrowAndLeavesFieldsNull() {
        var paymentObj = buildPaymentObject("PIX", "PENDING", null);
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPixQrCode()).isNull();
        assertThat(captor.getValue().getPixQrCodeBase64()).isNull();
        assertThat(captor.getValue().getPixExpiresAt()).isNull();
    }

    // -----------------------------------------------------------------------
    // CREDIT_CARD — PIX fields must NOT be populated
    // -----------------------------------------------------------------------

    @Test
    void handle_creditCardPayment_doesNotPopulatePixFields() {
        var paymentObj = buildPaymentObject("CREDIT_CARD", "CONFIRMED", null);
        var event = buildEvent(paymentObj);
        existingPayment = Payment.builder()
                .id(2L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .externalReference("BILLING-42")
                .build();

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPixQrCode()).isNull();
        assertThat(captor.getValue().getPixQrCodeBase64()).isNull();
        assertThat(captor.getValue().getPixExpiresAt()).isNull();
    }

    // -----------------------------------------------------------------------
    // PIX — both expirationDate and dueDate absent → pixExpiresAt stays null
    // -----------------------------------------------------------------------

    @Test
    void handle_pixPayment_nullDueDateAndNullExpiration_pixExpiresAtRemainsNull() {
        var pixQrCode = new AsaasDTO.PixQrCode("base64==", "payload...", null);
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-null", pixQrCode);
        // dueDate is explicitly null
        var paymentObj = new AsaasDTO.PaymentObject(
                "pay-001", "cust-001", "sub-001", "PENDING",
                BigDecimal.valueOf(99.00), null,
                null, null, null, "PIX", "BILLING-42",
                null, null, null, null, null, null, null, null, null, pixTransaction
        );
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPixExpiresAt()).isNull();
    }

    // -----------------------------------------------------------------------
    // PIX — pixTransaction present but qrCode is null → no NPE, fields null
    // -----------------------------------------------------------------------

    @Test
    void handle_pixPayment_pixTransactionWithNullQrCode_doesNotThrowAndLeavesFieldsNull() {
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-no-qr", null);
        var paymentObj = buildPaymentObject("PIX", "PENDING", pixTransaction);
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPixQrCode()).isNull();
        assertThat(captor.getValue().getPixQrCodeBase64()).isNull();
        assertThat(captor.getValue().getPixExpiresAt()).isNull();
    }

    // -----------------------------------------------------------------------
    // PIX — encodedImage blank → pixQrCodeBase64 not persisted (null)
    // -----------------------------------------------------------------------

    @Test
    void handle_pixPayment_encodedImageBlank_pixQrCodeBase64IsNull() {
        var pixQrCode = new AsaasDTO.PixQrCode(
                "   ",
                "00020126580014br.gov.bcb.pix...",
                LocalDateTime.of(2026, 5, 15, 23, 59, 59)
        );
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-blank", pixQrCode);
        var paymentObj = buildPaymentObject("PIX", "PENDING", pixTransaction);
        var event = buildEvent(paymentObj);

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(any())).thenReturn(existingPayment);

        handler.handle(event);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getPixQrCodeBase64()).isNull();
        assertThat(captor.getValue().getPixQrCode()).isEqualTo("00020126580014br.gov.bcb.pix...");
    }

    // -----------------------------------------------------------------------
    // PIX — multiple PAYMENT_CREATED webhooks → each payment saved independently
    // -----------------------------------------------------------------------

    @Test
    void handle_multiplePix_differentPayments_eachSavedIndependently() {
        var pixQrCode = new AsaasDTO.PixQrCode("base64==", "payload...", LocalDateTime.of(2026, 5, 15, 23, 59, 59));
        var pixTransaction = new AsaasDTO.PixTransaction("pix-tx-a", pixQrCode);

        var paymentObjA = buildPaymentObject("PIX", "PENDING", pixTransaction);
        var eventA = buildEvent(paymentObjA);

        Payment paymentB = Payment.builder()
                .id(2L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(4900)
                .currency("BRL")
                .externalReference("BILLING-43")
                .build();

        var paymentObjB = new AsaasDTO.PaymentObject(
                "pay-002", "cust-002", "sub-001", "PENDING",
                BigDecimal.valueOf(49.00), LocalDate.of(2026, 6, 11),
                null, null, null, "PIX", "BILLING-43",
                null, null, null, null, null, null, null, null, null, pixTransaction
        );
        var eventB = new AsaasDTO.WebhookCheckoutEvent(
                "evt-002", "PAYMENT_CREATED", "2026-05-02T10:00:00",
                null, null, paymentObjB, null
        );

        when(paymentRepository.findByExternalReference("BILLING-42")).thenReturn(Optional.of(existingPayment));
        when(paymentRepository.findByExternalReference("BILLING-43")).thenReturn(Optional.of(paymentB));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(eventA);
        handler.handle(eventB);

        verify(paymentRepository, times(2)).save(any(Payment.class));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private AsaasDTO.PaymentObject buildPaymentObject(String billingType, String status,
                                                       AsaasDTO.PixTransaction pixTransaction) {
        // id, customer, subscription, status, value, dueDate, creditDate, description,
        // checkoutSession, billingType, externalReference, invoiceUrl, transactionReceiptUrl,
        // nossoNumero, invoiceNumber, netValue, confirmedDate, paymentDate, installment,
        // discount, pixTransaction
        return new AsaasDTO.PaymentObject(
                "pay-001", "cust-001", "sub-001", status,
                BigDecimal.valueOf(99.00), LocalDate.of(2026, 5, 11),
                null, null, null, billingType, "BILLING-42",
                null, null, null, null, null, null, null, null, null, pixTransaction
        );
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(AsaasDTO.PaymentObject paymentObj) {
        return new AsaasDTO.WebhookCheckoutEvent(
                "evt-001", "PAYMENT_CREATED", "2026-05-01T10:00:00",
                null, null, paymentObj, null
        );
    }
}
