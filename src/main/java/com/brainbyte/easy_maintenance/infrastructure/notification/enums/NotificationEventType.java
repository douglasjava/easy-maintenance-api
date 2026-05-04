package com.brainbyte.easy_maintenance.infrastructure.notification.enums;

public enum NotificationEventType {
    // Operational flow events (managed by BusinessEmailNotificationService)
    ITEM_NEAR_DUE,
    ITEM_OVERDUE,
    MAINTENANCE_NEAR_DUE,
    MAINTENANCE_OVERDUE,

    // Critical transactional emails (managed by CriticalEmailDispatchService)
    TRIAL_EXPIRING,
    TRIAL_ACTIVATED,
    SUBSCRIPTION_CANCELLED,
    SUBSCRIPTION_BLOCKED,
    PAYMENT_PIX_OVERDUE,
    PASSWORD_RESET,
    TWO_FACTOR_RECOVERY
}
