package com.brainbyte.easy_maintenance.billing.application.dto.response;

import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import lombok.Builder;

import java.time.Instant;

/**
 * Response for GET /billing/pending-payment.
 *
 * <p>Contains the most recent PENDING payment for the user's active subscription.
 * PIX-specific fields (pixQrCode, pixQrCodeBase64, pixExpiresAt) are populated
 * only when methodType == PIX. Null when no pending payment exists.
 */
@Builder
public record PendingPaymentResponse(
        Long paymentId,
        PaymentMethodType methodType,
        PaymentStatus status,
        Integer amountCents,
        String currency,
        String paymentLink,

        // PIX-specific — null for CREDIT_CARD payments
        String pixQrCode,
        String pixQrCodeBase64,
        Instant pixExpiresAt
) {
}
