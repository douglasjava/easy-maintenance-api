package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record BillingSubscriptionResponse(
        String id,
        SubscriptionStatus status,
        BillingCycle cycle,
        Instant trialEndsAt,
        LocalDate nextDueDate,
        Instant createdAt
) {

    public record SubscriptionItemResponse(
            Long id,
            String sourceId,
            String sourceType,
            String planCode,
            String planName,
            Long valueCents,
            String nextPlanCode,
            Instant planChangeEffectiveAt
    ) {}
}
