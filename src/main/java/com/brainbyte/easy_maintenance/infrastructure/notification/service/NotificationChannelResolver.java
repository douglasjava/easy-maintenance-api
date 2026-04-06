package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class NotificationChannelResolver {

    public Set<NotificationChannel> resolveChannels(NotificationEvent event) {
        NotificationEventType type = event.getEventType();
        
        return switch (type) {
            case ITEM_NEAR_DUE, MAINTENANCE_NEAR_DUE -> EnumSet.of(NotificationChannel.PUSH);
            case ITEM_OVERDUE, MAINTENANCE_OVERDUE -> EnumSet.of(NotificationChannel.PUSH, NotificationChannel.EMAIL);
        };
    }

}
