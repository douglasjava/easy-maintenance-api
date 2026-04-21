package com.brainbyte.easy_maintenance.billing.domain.enums;

public enum SubscriptionStatus {
    TRIAL,
    TRIAL_EXPIRED,
    PENDING_PAYMENT,
    ACTIVE,
    PAST_DUE,
    BLOCKED,
    CANCELED,
    NONE,
    PENDING_ACTIVATION,
    PAYMENT_FAILED
}
