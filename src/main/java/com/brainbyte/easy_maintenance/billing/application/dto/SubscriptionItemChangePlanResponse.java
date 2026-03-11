package com.brainbyte.easy_maintenance.billing.application.dto;

import lombok.Builder;

@Builder
public record SubscriptionItemChangePlanResponse(
    Long subscriptionItemId,
    String currentPlan,
    String newPlan,
    String effectiveAt,
    boolean applyImmediately,
    String message
) {}
