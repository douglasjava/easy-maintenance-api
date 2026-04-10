package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.InAppNotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
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
    private final InAppNotificationService inAppNotificationService;

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

        // Always save in-app notification regardless of other channel results
        try {
            saveInApp(event);
        } catch (Exception e) {
            log.error("[Orchestrator] Erro ao salvar notificação in-app para evento {}: {}",
                    event.getEventType(), e.getMessage());
        }
    }

    private void saveInApp(NotificationEvent event) {
        if (event.getOrganizationCode() == null) return;

        InAppNotificationType type = resolveInAppType(event.getEventType());
        String title = resolveInAppTitle(event);
        String body = resolveInAppBody(event);

        inAppNotificationService.saveForOrg(
                event.getOrganizationCode(), title, body, type, event.getReferenceId());
    }

    private InAppNotificationType resolveInAppType(NotificationEventType eventType) {
        return switch (eventType) {
            case ITEM_NEAR_DUE, ITEM_OVERDUE -> InAppNotificationType.ITEM_DUE;
            case MAINTENANCE_NEAR_DUE, MAINTENANCE_OVERDUE -> InAppNotificationType.MAINTENANCE_DUE;
        };
    }

    private String resolveInAppTitle(NotificationEvent event) {
        return switch (event.getEventType()) {
            case ITEM_NEAR_DUE -> "Item próximo do vencimento";
            case ITEM_OVERDUE -> "Item vencido";
            case MAINTENANCE_NEAR_DUE -> "Manutenção próxima do vencimento";
            case MAINTENANCE_OVERDUE -> "Manutenção vencida";
        };
    }

    private String resolveInAppBody(NotificationEvent event) {
        String ref = switch (event.getReferenceType()) {
            case ITEM -> "Item";
            case MAINTENANCE -> "Manutenção";
        };
        int days = event.getDaysOffset();
        String status = days > 0 ? "vence em " + days + " dia(s)" :
                        days == 0 ? "vence hoje" :
                        "está em atraso há " + Math.abs(days) + " dia(s)";
        return ref + " (ID: " + event.getReferenceId() + ") " + status + ".";
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
