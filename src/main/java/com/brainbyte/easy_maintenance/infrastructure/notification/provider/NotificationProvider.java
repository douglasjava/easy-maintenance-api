package com.brainbyte.easy_maintenance.infrastructure.notification.provider;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationType;

public interface NotificationProvider {

    void send(NotificationPayload payload);

    NotificationType getType();

}
