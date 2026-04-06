package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private String organizationCode;
    private NotificationEventType eventType;
    private NotificationReferenceType referenceType;
    private Long referenceId;
    private LocalDate dueDate;
    private int daysOffset;
    private String recipient;

}
