package com.brainbyte.easy_maintenance.payment.application.dto;

import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CreatePaymentRequest(
        @NotNull Long invoiceId,
        @NotNull Long payerUserId,
        @NotNull PaymentProvider provider,
        @NotNull PaymentMethodType methodType,
        @NotNull Integer amountCents,
        String currency,
        String externalReference
) {
}
