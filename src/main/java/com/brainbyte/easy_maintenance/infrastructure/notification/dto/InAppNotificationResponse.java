package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.InAppNotificationType;

import java.time.Instant;

public record InAppNotificationResponse(
        Long id,
        String title,
        String body,
        InAppNotificationType type,
        Long referenceId,
        String referenceLabel,
        boolean read,
        Instant createdAt,
        String orgCode
) {}
