package com.brainbyte.easy_maintenance.billing.application.dto;

import lombok.Builder;

@Builder
public record SubscriptionItemCancelResponse(
    Long subscriptionItemId,
    String message,
    boolean scheduled
) {}
