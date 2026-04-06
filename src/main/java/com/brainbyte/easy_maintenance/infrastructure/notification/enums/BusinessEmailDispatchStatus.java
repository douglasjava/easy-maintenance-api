package com.brainbyte.easy_maintenance.infrastructure.notification.enums;

public enum BusinessEmailDispatchStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED_LIMIT,
    SKIPPED_INVALID_RECIPIENT,
    SKIPPED_UNSUPPORTED_EVENT
}
