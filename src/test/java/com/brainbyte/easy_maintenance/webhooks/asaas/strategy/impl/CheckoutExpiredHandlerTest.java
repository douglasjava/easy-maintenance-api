package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckoutExpiredHandlerTest {

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

    @InjectMocks
    private CheckoutExpiredHandler handler;

    private Invoice invoice;
    private BillingSubscription subscription;
    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        invoice = Invoice.builder().id(1L).status(InvoiceStatus.OPEN).build();
        subscription = BillingSubscription.builder().id(5L).status(SubscriptionStatus.PENDING_PAYMENT).build();
        payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.PENDING)
                .invoice(invoice)
                .payer(User.builder().id(10L).build())
                .billingSubscription(subscription)
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldReturnCheckoutExpiredEventType() {
        assertThat(handler.getEventType()).isEqualTo("CHECKOUT_EXPIRED");
    }

    @Test
    void handle_happyPath_shouldMarkPaymentExpiredAndCancelInvoice() {
        var checkout = buildCheckout("checkout-001");
        var event = buildEvent("checkout-001", checkout);

        when(paymentRepository.findByExternalPaymentId("checkout-001")).thenReturn(Optional.of(payment));

        handler.handle(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(payment.getFailureReason()).isEqualTo("CHECKOUT_EXPIRED");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.CANCELED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);

        verify(paymentRepository).save(payment);
        verify(invoiceRepository).save(invoice);
        verify(paymentGatewayEventRepository).save(any());
    }

    @Test
    void handle_paymentAlreadyFinal_shouldSkipUpdate() {
        payment.setStatus(PaymentStatus.EXPIRED);
        var checkout = buildCheckout("checkout-002");
        var event = buildEvent("checkout-002", checkout);

        when(paymentRepository.findByExternalPaymentId("checkout-002")).thenReturn(Optional.of(payment));

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    void handle_nullCheckout_shouldSaveGatewayEventAndReturn() {
        var event = buildEvent("some-id", null);

        handler.handle(event);

        verify(paymentRepository, never()).findByExternalPaymentId(any());
        verify(paymentGatewayEventRepository).save(any());
    }

    @Test
    void handle_paymentNotFound_shouldLogWarnAndNotThrow() {
        var checkout = buildCheckout("checkout-missing");
        var event = buildEvent("checkout-missing", checkout);

        when(paymentRepository.findByExternalPaymentId("checkout-missing")).thenReturn(Optional.empty());

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handle_paymentWithNullInvoice_shouldNotThrow() {
        payment.setInvoice(null);
        var checkout = buildCheckout("checkout-003");
        var event = buildEvent("checkout-003", checkout);

        when(paymentRepository.findByExternalPaymentId("checkout-003")).thenReturn(Optional.of(payment));

        handler.handle(event);

        verify(paymentRepository).save(payment);
        verify(invoiceRepository, never()).save(any());
    }

    private AsaasDTO.WebhookCheckout buildCheckout(String checkoutId) {
        return new AsaasDTO.WebhookCheckout(
                checkoutId, "https://link", "EXPIRED", 30,
                null, null, null, null, null, "cust-001"
        );
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String eventId, AsaasDTO.WebhookCheckout checkout) {
        return new AsaasDTO.WebhookCheckoutEvent(
                eventId, "CHECKOUT_EXPIRED", "2026-04-30T10:00:00",
                null, checkout, null, null
        );
    }
}
