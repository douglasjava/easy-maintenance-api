package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.mail.MailService;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailNotificationProvider implements NotificationProvider {

    private final MailService mailerSendService;

    @Override
    public void send(NotificationPayload payload) {
        mailerSendService.sendEmail(
                payload.getRecipient(),
                payload.getRecipientName(),
                payload.getSubject(),
                payload.getContent(),
                payload.getHtmlContent()
        );
    }

    @Override
    public NotificationType getType() {
        return NotificationType.EMAIL;
    }

}
