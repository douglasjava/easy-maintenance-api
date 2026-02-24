package com.brainbyte.easy_maintenance.payment.application.dto;

import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodStatus;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class PaymentMethodDTO {

    public record CreatePaymentMethodRequest(
            @NotNull @Schema(description = "Tipo do método de pagamento") PaymentMethodType methodType,
            @NotNull @Schema(description = "Provider do pagamento") PaymentProvider provider,
            @NotBlank @Schema(description = "ID externo do método de pagamento (token)") String externalId,
            @Schema(description = "Define como método padrão") boolean isDefault
    ) {}

    public record PaymentMethodResponse(
            Long id,
            Long userId,
            PaymentMethodType methodType,
            PaymentProvider provider,
            String externalId,
            PaymentMethodStatus status,
            boolean isDefault,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
