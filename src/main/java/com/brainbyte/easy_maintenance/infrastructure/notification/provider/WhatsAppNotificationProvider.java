package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WhatsAppNotificationProvider implements NotificationProvider {
    @Override
    public void send(NotificationPayload payload) {
        log.info("Sending WHATSAPP notification to {}: {}", payload.getRecipient(), payload.getSubject());
        // TODO: Implement Twilio or Meta WhatsApp Business API integration
    }

    @Override
    public NotificationType getType() {
        return NotificationType.WHATSAPP;
    }
}
