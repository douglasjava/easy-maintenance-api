package com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.PaymentMethodTransitionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionCreatedHandlerTest {

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
    @Mock private AsaasClient asaasClient;

    @InjectMocks
    private SubscriptionCreatedHandler handler;

    private BillingSubscription subscription;
    private Payment payment;

    @BeforeEach
    void setUp() throws Exception {
        subscription = BillingSubscription.builder()
                .id(5L)
                .status(SubscriptionStatus.PENDING_ACTIVATION)
                .build();
        payment = Payment.builder()
                .id(1L)
                .status(PaymentStatus.CHECKOUT_PAID)
                .payer(User.builder().id(10L).build())
                .billingSubscription(subscription)
                .build();

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldReturnSubscriptionCreatedEventType() {
        assertThat(handler.getEventType()).isEqualTo("SUBSCRIPTION_CREATED");
    }

    @Test
    void handle_happyPath_shouldActivateBillingSubscription() {
        var asaasSub = buildSubscription("ext-sub-001", "checkout-session-001", "ACTIVE", false,
                LocalDate.of(2026, 5, 30));
        var event = buildEvent("evt-001", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-001")).thenReturn(Optional.of(payment));

        handler.handle(event);

        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("ext-sub-001");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextDueDate()).isEqualTo(LocalDate.of(2026, 5, 30));

        verify(billingSubscriptionRepository).save(subscription);
        verify(paymentGatewayEventRepository).save(any());
    }

    @Test
    void handle_subscriptionAlreadyLinked_shouldSkipActivation() {
        subscription.setExternalSubscriptionId("already-linked-ext-id");
        var asaasSub = buildSubscription("ext-sub-new", "checkout-session-001", "ACTIVE", false,
                LocalDate.of(2026, 5, 30));
        var event = buildEvent("evt-002", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-001")).thenReturn(Optional.of(payment));

        handler.handle(event);

        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("already-linked-ext-id");
        verify(billingSubscriptionRepository, never()).save(any());
    }

    @Test
    void handle_cardUpdatePrefix_shouldReplaceExternalSubIdAndCancelOld() {
        subscription.setExternalSubscriptionId("old-ext-sub-001");
        payment.setExternalReference(PaymentMethodTransitionService.CARD_UPDATE_PREFIX + "20-uuid-xyz");

        var asaasSub = buildSubscription("new-ext-sub-002", "checkout-session-card-update", "ACTIVE", false,
                LocalDate.of(2026, 9, 1));
        var event = buildEvent("evt-007", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-card-update")).thenReturn(Optional.of(payment));

        handler.handle(event);

        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("new-ext-sub-002");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(billingSubscriptionRepository).save(subscription);
        verify(asaasClient).cancelSubscription("old-ext-sub-001");
    }

    @Test
    void handle_cardUpdatePrefix_cancelOldFails_doesNotThrow() {
        subscription.setExternalSubscriptionId("old-ext-sub-999");
        payment.setExternalReference(PaymentMethodTransitionService.CARD_UPDATE_PREFIX + "20-uuid-abc");

        var asaasSub = buildSubscription("new-ext-sub-888", "checkout-session-update-2", "ACTIVE", false,
                LocalDate.of(2026, 9, 1));
        var event = buildEvent("evt-008", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-update-2")).thenReturn(Optional.of(payment));
        doThrow(new RuntimeException("Asaas 404")).when(asaasClient).cancelSubscription(any());

        handler.handle(event);

        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("new-ext-sub-888");
        verify(billingSubscriptionRepository).save(subscription);
    }

    @Test
    void handle_subscriptionDeleted_shouldSkipActivation() {
        var asaasSub = buildSubscription("ext-sub-001", "checkout-session-001", "ACTIVE", true,
                LocalDate.of(2026, 5, 30));
        var event = buildEvent("evt-003", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-001")).thenReturn(Optional.of(payment));

        handler.handle(event);

        verify(billingSubscriptionRepository, never()).save(any());
    }

    @Test
    void handle_subscriptionNotActive_shouldSkipActivation() {
        var asaasSub = buildSubscription("ext-sub-001", "checkout-session-001", "INACTIVE", false,
                LocalDate.of(2026, 5, 30));
        var event = buildEvent("evt-004", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-001")).thenReturn(Optional.of(payment));

        handler.handle(event);

        verify(billingSubscriptionRepository, never()).save(any());
    }

    @Test
    void handle_nullSubscriptionObject_shouldSaveGatewayEventAndReturn() {
        var event = new AsaasDTO.WebhookCheckoutEvent(
                "evt-005", "SUBSCRIPTION_CREATED", "2026-04-30T10:00:00",
                null, null, null, null
        );

        handler.handle(event);

        verify(paymentRepository, never()).findByExternalPaymentId(any());
        verify(paymentGatewayEventRepository).save(any());
    }

    @Test
    void handle_paymentNotFound_shouldNotThrow() {
        var asaasSub = buildSubscription("ext-sub-001", "checkout-session-missing", "ACTIVE", false,
                LocalDate.of(2026, 5, 30));
        var event = buildEvent("evt-006", asaasSub);

        when(paymentRepository.findByExternalPaymentId("checkout-session-missing")).thenReturn(Optional.empty());

        handler.handle(event);

        verify(billingSubscriptionRepository, never()).save(any());
    }

    private AsaasDTO.WebhookSubscription buildSubscription(String id, String checkoutSession,
                                                            String status, boolean deleted,
                                                            LocalDate nextDueDate) {
        return new AsaasDTO.WebhookSubscription(id, "ref-001", checkoutSession, nextDueDate,
                "2026-04-01", status, deleted);
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String eventId, AsaasDTO.WebhookSubscription subscription) {
        return new AsaasDTO.WebhookCheckoutEvent(
                eventId, "SUBSCRIPTION_CREATED", "2026-04-30T10:00:00",
                null, null, null, subscription
        );
    }
}
