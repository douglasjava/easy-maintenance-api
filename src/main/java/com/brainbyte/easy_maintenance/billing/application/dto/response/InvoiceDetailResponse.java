package com.brainbyte.easy_maintenance.billing.application.dto.response;

import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Builder
public record InvoiceDetailResponse(
    InvoiceResponse invoice,
    List<InvoiceItemResponse> items,
    PaymentResponse payment,
    BillingResponse billing
) {

    @Builder
    public record InvoiceResponse(
        Long id,
        InvoiceStatus status,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dueDate,
        Integer subtotalCents,
        Integer discountCents,
        Integer totalCents
    ) {}

    @Builder
    public record InvoiceItemResponse(
        Long id,
        String type,
        String sourceId,
        String description,
        String planCode,
        Integer quantity,
        Integer unitAmountCents,
        Integer amountCents
    ) {}

    @Builder
    public record PaymentResponse(
        Long paymentId,
        PaymentProvider provider,
        PaymentMethodType method,
        PaymentStatus status,
        String paymentLink,
        String pixQrCode,
        String pixQrCodeBase64,
        Instant paidAt
    ) {}

    @Builder
    public record BillingResponse(
        String name,
        String document,
        String email
    ) {}
}
