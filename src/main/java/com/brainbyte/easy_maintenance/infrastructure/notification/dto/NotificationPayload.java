package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NotificationPayload {
    private Long idUser;
    private String recipient; // Email, Phone number, or Device Token
    private String recipientName;
    private String subject;
    private String content; // Generic text content
    private String htmlContent; // HTML specific for Email
    private Map<String, Object> templateData;
    private String templateName;
}
