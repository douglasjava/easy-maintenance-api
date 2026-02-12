package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.NotificationProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationService {

    private final Map<NotificationType, NotificationProvider> providers;
    private final NotificationTemplateBuilder templateBuilder;

    public NotificationService(List<NotificationProvider> providerList, NotificationTemplateBuilder templateBuilder) {
        this.providers = providerList.stream().collect(Collectors.toMap(NotificationProvider::getType, p -> p));
        this.templateBuilder = templateBuilder;
    }

    public void send(NotificationType type, NotificationPayload payload) {
        log.info("Processing notification of type {} to {}", type, payload.getRecipient());

        // Fill content from template if needed
        if (payload.getTemplateName() != null && !payload.getTemplateName().isBlank()) {
            if (payload.getContent() == null || payload.getContent().isBlank()) {
                payload.setContent(templateBuilder.buildText(payload.getTemplateName(), payload.getTemplateData()));
            }
            if (type == NotificationType.EMAIL && (payload.getHtmlContent() == null || payload.getHtmlContent().isBlank())) {
                payload.setHtmlContent(templateBuilder.buildHtml(payload.getTemplateName(), payload.getTemplateData()));
            }
        }

        NotificationProvider provider = providers.get(type);
        if (provider == null) {
            log.error("No provider found for notification type: {}", type);
            return;
        }

        provider.send(payload);
    }
}
