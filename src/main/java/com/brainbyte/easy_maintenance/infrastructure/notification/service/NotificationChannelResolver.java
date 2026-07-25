package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Service
public class NotificationChannelResolver {

    @Value("${notification.whatsapp.urgent-threshold-hours:48}")
    private int urgentThresholdHours;

    public Set<NotificationChannel> resolveChannels(NotificationEvent event) {
        NotificationEventType type = event.getEventType();

        return switch (type) {
            case ITEM_NEAR_DUE, MAINTENANCE_NEAR_DUE -> nearDueChannels(event);
            // Já vencido: sempre dentro da janela de urgência (ver TASK-130) — inclui WHATSAPP
            // incondicionalmente, ao contrário de NEAR_DUE, que só inclui dentro do limiar.
            case ITEM_OVERDUE, MAINTENANCE_OVERDUE ->
                    EnumSet.of(NotificationChannel.PUSH, NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
            // Critical transactional emails are dispatched via CriticalEmailDispatchService, not this resolver
            default -> EnumSet.noneOf(NotificationChannel.class);
        };
    }

    private Set<NotificationChannel> nearDueChannels(NotificationEvent event) {
        EnumSet<NotificationChannel> channels = EnumSet.of(NotificationChannel.PUSH);
        if (isWithinUrgentWindow(event)) {
            channels.add(NotificationChannel.WHATSAPP);
        }
        return channels;
    }

    // TASK-130: NotificationEvent só carrega daysOffset em dias inteiros (checkpoints fixos
    // {30,15,7,1} para NEAR_DUE, produzidos por NotificationEventDetectionService) — não existe
    // sinal contínuo de hora. O limiar configurável em horas é arredondado para cima em dias
    // inteiros; com o default de 48h (2 dias), na prática só o checkpoint daysOffset==1 é
    // alcançado hoje (não há checkpoint em daysOffset==2). Decisão de v1 (ver EPIC-015/TASK-130
    // Riscos): aceitar essa granularidade — ajustar NotificationEventDetectionService para um
    // checkpoint de hora real é uma mudança maior, fora do escopo desta task.
    private boolean isWithinUrgentWindow(NotificationEvent event) {
        int thresholdDays = (int) Math.ceil(urgentThresholdHours / 24.0);
        return event.getDaysOffset() <= thresholdDays;
    }
}
