package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class UserSubscriptionDTO {

    public record SubscriptionResponse(
            Long id,
            Long userId,
            String payerEmail,
            String planCode,
            String planName,
            Long priceCents,
            SubscriptionStatus status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialEndsAt,
            Boolean cancelAtPeriodEnd,
            Instant canceledAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdateSubscriptionRequest(
            @NotNull Long userId,
            @NotBlank String planCode,
            @NotNull SubscriptionStatus status,
            @NotNull Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {}
}
