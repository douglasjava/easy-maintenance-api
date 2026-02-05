package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class InvoiceDTO {

    public record InvoiceResponse(
            Long id,
            Long payerUserId,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd,
            InvoiceStatus status,
            LocalDate dueDate,
            Integer subtotalCents,
            Integer discountCents,
            Integer totalCents,
            List<InvoiceItemResponse> items,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record InvoiceItemResponse(
            Long id,
            String organizationCode,
            String planCode,
            String description,
            Integer quantity,
            Integer unitAmountCents,
            Integer amountCents,
            Instant createdAt
    ) {}

    public record BillingSummaryResponse(
            BillingAccountDTO.BillingAccountResponse account,
            InvoiceResponse currentOpenInvoice
    ) {}
}
