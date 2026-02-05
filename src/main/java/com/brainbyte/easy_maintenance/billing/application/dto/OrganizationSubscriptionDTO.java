package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class OrganizationSubscriptionDTO {

    public record SubscriptionResponse(
            Long id,
            Long organizationId,
            String organizationCode,
            String organizationName,
            Long payerUserId,
            String payerEmail,
            String planCode,
            String planName,
            Integer priceCents,
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
            @NotNull Long payerUserId,
            @NotBlank String planCode,
            @NotNull SubscriptionStatus status,
            @NotNull Instant currentPeriodStart,
            Instant currentPeriodEnd
    ) {}
}
