package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class BillingPlanDTO {

    public record BillingPlanResponse(
            Long id,
            String code,
            String name,
            String currency,
            BillingCycle billingCycle,
            Integer priceCents,
            String featuresJson,
            BillingStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateBillingPlanRequest(
            @NotBlank String code,
            @NotBlank String name,
            @NotBlank String currency,
            @NotNull BillingCycle billingCycle,
            @NotNull Integer priceCents,
            String featuresJson,
            @NotNull BillingStatus status
    ) {}

    public record UpdateBillingPlanRequest(
            String name,
            String currency,
            BillingCycle billingCycle,
            Integer priceCents,
            String featuresJson,
            BillingStatus status
    ) {}
}
