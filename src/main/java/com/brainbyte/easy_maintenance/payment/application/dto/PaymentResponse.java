package com.brainbyte.easy_maintenance.payment.application.dto;

import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import lombok.Builder;

import java.time.Instant;

@Builder
public record PaymentResponse(
        Long id,
        Long invoiceId,
        Long payerUserId,
        PaymentProvider provider,
        PaymentMethodType methodType,
        PaymentStatus status,
        Integer amountCents,
        String currency,
        String externalPaymentId,
        String externalReference,
        String pixQrCode,
        String pixQrCodeBase64,
        String paymentLink,
        String failureReason,
        Instant paidAt,
        Instant createdAt,
        Instant updatedAt
) {
}
