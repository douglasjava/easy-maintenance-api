package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingDashboardService.getPendingPayment().
 * Covers: no subscription, PENDING found, OVERDUE fallback, and no payment at all.
 */
@ExtendWith(MockitoExtension.class)
class BillingDashboardServicePendingPaymentTest {

    @Mock BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock BillingAccountRepository billingAccountRepository;
    @Mock BillingSubscriptionItemRepository itemRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock OrganizationRepository organizationRepository;

    @InjectMocks
    BillingDashboardService service;

    private static final long USER_ID = 99L;
    private static final long SUBSCRIPTION_ID = 42L;

    // -----------------------------------------------------------------------
    // No subscription → null (normal for users not yet subscribed)
    // -----------------------------------------------------------------------

    @Test
    void getPendingPayment_noSubscription_returnsNull() {
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.empty());

        var result = service.getPendingPayment(USER_ID);

        assertThat(result).isNull();
        verifyNoInteractions(paymentRepository);
    }

    // -----------------------------------------------------------------------
    // PENDING payment exists → returned with all fields mapped
    // -----------------------------------------------------------------------

    @Test
    void getPendingPayment_pendingPaymentExists_returnsMappedResponse() {
        var subscription = mockSubscription();
        var pixExpiresAt = Instant.parse("2026-05-15T23:59:59Z");
        var pendingPayment = Payment.builder()
                .id(10L)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(9900)
                .currency("BRL")
                .paymentLink("https://pay.asaas.com/i/pix-link")
                .pixQrCode("00020126580014br.gov.bcb.pix...")
                .pixQrCodeBase64("base64ImageData==")
                .pixExpiresAt(pixExpiresAt)
                .build();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(Optional.of(pendingPayment));

        var result = service.getPendingPayment(USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.paymentId()).isEqualTo(10L);
        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.methodType()).isEqualTo(PaymentMethodType.PIX);
        assertThat(result.pixQrCode()).isEqualTo("00020126580014br.gov.bcb.pix...");
        assertThat(result.pixQrCodeBase64()).isEqualTo("base64ImageData==");
        assertThat(result.pixExpiresAt()).isEqualTo(pixExpiresAt);
    }

    // -----------------------------------------------------------------------
    // No PENDING, OVERDUE exists → returned (shows red banner on billing page)
    // -----------------------------------------------------------------------

    @Test
    void getPendingPayment_noPendingButOverdueExists_returnsOverduePayment() {
        var subscription = mockSubscription();
        var overduePayment = Payment.builder()
                .id(11L)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.OVERDUE)
                .amountCents(9900)
                .currency("BRL")
                .provider(PaymentProvider.ASAAS)
                .build();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                SUBSCRIPTION_ID, PaymentStatus.OVERDUE))
                .thenReturn(Optional.of(overduePayment));

        var result = service.getPendingPayment(USER_ID);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(PaymentStatus.OVERDUE);
        assertThat(result.paymentId()).isEqualTo(11L);
    }

    // -----------------------------------------------------------------------
    // No PENDING and no OVERDUE → null (204 from controller)
    // -----------------------------------------------------------------------

    @Test
    void getPendingPayment_noPaymentAtAll_returnsNull() {
        var subscription = mockSubscription();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                SUBSCRIPTION_ID, PaymentStatus.OVERDUE))
                .thenReturn(Optional.empty());

        var result = service.getPendingPayment(USER_ID);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private BillingSubscription mockSubscription() {
        var sub = mock(BillingSubscription.class);
        when(sub.getId()).thenReturn(SUBSCRIPTION_ID);
        return sub;
    }
}
