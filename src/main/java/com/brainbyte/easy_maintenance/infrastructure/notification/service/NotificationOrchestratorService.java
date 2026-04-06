package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOrchestratorService {

    private final NotificationChannelResolver channelResolver;
    private final BusinessPushNotificationService pushNotificationService;
    private final BusinessEmailNotificationService emailNotificationService;

    public void dispatch(NotificationEvent event) {
        log.debug("[Orchestrator] Despachando evento: {}", event.getEventType());
        
        Set<NotificationChannel> channels = channelResolver.resolveChannels(event);
        log.debug("[Orchestrator] Canais resolvidos para o evento: {}", channels);

        for (NotificationChannel channel : channels) {
            try {
                switch (channel) {
                    case PUSH -> pushNotificationService.sendPush(event);
                    case EMAIL -> emailNotificationService.sendEmail(event);
                    case WHATSAPP -> log.warn("[Orchestrator] Canal WHATSAPP ainda não implementado. Ignorando para evento {}", event.getEventType());
                    default -> log.warn("[Orchestrator] Canal não suportado: {}", channel);
                }
            } catch (Exception e) {
                log.error("[Orchestrator] Erro ao despachar evento {} para o canal {}: {}", 
                        event.getEventType(), channel, e.getMessage());
            }
        }
    }

    public void dispatch(List<NotificationEvent> events) {
        if (events == null || events.isEmpty()) {
            log.info("[Orchestrator] Nenhuns eventos para despachar.");
            return;
        }

        log.info("[Orchestrator] Iniciando despacho de {} eventos.", events.size());
        events.forEach(this::dispatch);
        log.info("[Orchestrator] Despacho de {} eventos finalizado.", events.size());
    }

}
