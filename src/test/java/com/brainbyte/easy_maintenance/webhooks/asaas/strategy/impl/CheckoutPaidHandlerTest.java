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
class CheckoutPaidHandlerTest {

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
    private CheckoutPaidHandler handler;

    private Invoice invoice;
    private BillingSubscription subscription;
    private User payer;
    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        payer = User.builder().id(10L).email("user@test.com").build();
        invoice = Invoice.builder().id(1L).status(InvoiceStatus.OPEN).build();
        subscription = BillingSubscription.builder()
                .id(5L)
                .status(SubscriptionStatus.PENDING_ACTIVATION)
                .build();
        payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.PENDING)
                .invoice(invoice)
                .payer(payer)
                .billingSubscription(subscription)
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldReturnCheckoutPaidEventType() {
        assertThat(handler.getEventType()).isEqualTo("CHECKOUT_PAID");
    }

    @Test
    void handle_happyPath_shouldMarkPaymentPaidAndUpdateSubscription() {
        var checkout = buildCheckout("checkout-001");
        var event = buildEvent("checkout-001", checkout);

        when(paymentRepository.findByExternalPaymentId("checkout-001")).thenReturn(Optional.of(payment));
        var billingSubscription = BillingSubscription.builder().id(5L).status(SubscriptionStatus.PENDING_ACTIVATION).build();
        when(billingSubscriptionRepository.findByBillingAccountUserId(10L)).thenReturn(Optional.of(billingSubscription));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.handle(event);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CHECKOUT_PAID);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(billingSubscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING_ACTIVATION);

        verify(paymentRepository).save(payment);
        verify(invoiceRepository).save(invoice);
        verify(billingSubscriptionRepository).save(billingSubscription);
        verify(paymentGatewayEventRepository).save(any());
    }

    @Test
    void handle_paymentAlreadyFinal_shouldSkipUpdate() {
        payment.setStatus(PaymentStatus.PAID);
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
    void handle_paymentNotFound_shouldLogErrorAndNotThrow() {
        var checkout = buildCheckout("checkout-missing");
        var event = buildEvent("checkout-missing", checkout);

        when(paymentRepository.findByExternalPaymentId("checkout-missing")).thenReturn(Optional.empty());

        handler.handle(event);

        verify(paymentRepository, never()).save(any());
    }

    private AsaasDTO.WebhookCheckout buildCheckout(String checkoutId) {
        return new AsaasDTO.WebhookCheckout(
                checkoutId, "https://link", "PENDING", 30,
                null, null, null, null, null, "cust-001"
        );
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String eventId, AsaasDTO.WebhookCheckout checkout) {
        return new AsaasDTO.WebhookCheckoutEvent(
                eventId, "CHECKOUT_PAID", "2026-04-30T10:00:00",
                null, checkout, null, null
        );
    }
}
