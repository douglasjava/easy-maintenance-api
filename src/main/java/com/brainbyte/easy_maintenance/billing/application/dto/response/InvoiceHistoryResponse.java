package com.brainbyte.easy_maintenance.billing.application.dto.response;

import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;

@Builder
public record InvoiceHistoryResponse(
        Long invoiceId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dueDate,
        String currency,
        Integer subtotalCents,
        Integer discountCents,
        Integer totalCents,
        InvoiceStatus status,
        PaymentSummaryResponse payment
) {
    @Builder
    public record PaymentSummaryResponse(
            Long paymentId,
            PaymentProvider provider,
            PaymentMethodType method,
            PaymentStatus status,
            Instant paidAt,
            String paymentLink
    ) {}
}
