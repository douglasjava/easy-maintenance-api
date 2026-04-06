package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.infrastructure.notification.service.NotificationEventDetectionService;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.NotificationOrchestratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventDetectionJob {

    private final NotificationEventDetectionService detectionService;
    private final NotificationOrchestratorService orchestratorService;

    @Scheduled(cron = "${notification.detection.cron:0 0 5 * * *}") // Padrão: 5h da manhã
    public void run() {
        log.info("[NotificationJob] Iniciando job de detecção de eventos de notificação.");
        
        try {
            List<NotificationEvent> events = detectionService.detectEvents();
            
            if (events.isEmpty()) {
                log.info("[NotificationJob] Nenhum evento detectado hoje.");
            } else {
                log.info("[NotificationJob] Foram detectados {} eventos de notificação.", events.size());
                
                // Logando detalhes em nível debug conforme solicitado
                events.forEach(event -> 
                    log.debug("[NotificationJob] Evento detectado: Type={}, RefType={}, RefId={}, Org={}, Due={}, Offset={}",
                            event.getEventType(),
                            event.getReferenceType(),
                            event.getReferenceId(),
                            event.getOrganizationCode(),
                            event.getDueDate(),
                            event.getDaysOffset())
                );

                // Chama o orquestrador para despachar os eventos detectados
                orchestratorService.dispatch(events);
            }
            
            log.info("[NotificationJob] Job de detecção e orquestração finalizado com sucesso.");
            
        } catch (Exception e) {
            log.error("[NotificationJob] Erro inesperado durante execução do job: {}", e.getMessage(), e);
        }
    }
}
