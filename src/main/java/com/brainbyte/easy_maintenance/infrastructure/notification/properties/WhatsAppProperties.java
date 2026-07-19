package com.brainbyte.easy_maintenance.infrastructure.notification.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        String baseUrl,
        String apiToken,
        String phoneNumberId,
        String wabaId,
        String defaultTemplateName
) {
}
