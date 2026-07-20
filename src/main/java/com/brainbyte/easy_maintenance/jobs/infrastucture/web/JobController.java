package com.brainbyte.easy_maintenance.jobs.infrastucture.web;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.BusinessWhatsAppNotificationService;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.NotificationEventDetectionService;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.NotificationOrchestratorService;
import com.brainbyte.easy_maintenance.jobs.service.TrialExpirationService;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/run-jobs")
@Tag(name = "Executar Jobs", description = "Executar jobs")
public class JobController {

    private final TrialExpirationService trialExpirationService;
    private final NotificationEventDetectionService detectionService;
    private final NotificationOrchestratorService orchestratorService;
    private final BusinessWhatsAppDispatchRepository dispatchRepository;
    private final BusinessWhatsAppNotificationService whatsAppNotificationService;

    @GetMapping("/execute-trial-expiration")
    public void executeTrialExpirationJobService() {

        trialExpirationService.processTrialsExpiringWithinDays(2);

    }

    // TASK-132: dispara sob demanda o mesmo trabalho do NotificationEventDetectionJob (normalmente
    // só roda às 5h via cron) — usado por QA para não precisar esperar o cron pra validar um cenário
    // recém-montado no banco (ex.: next_due_at ajustado via SQL pra cair num checkpoint de 48h).
    @GetMapping("/execute-notification-detection")
    @Operation(summary = "Disparar detecção de eventos de notificação sob demanda (equivalente ao cron das 5h)")
    public Map<String, Object> executeNotificationDetectionJob() {
        TenantContext.setSystemContext();
        try {
            List<NotificationEvent> events = detectionService.detectEvents();
            if (!events.isEmpty()) {
                orchestratorService.dispatch(events);
            }
            return Map.of("eventsDetected", events.size());
        } finally {
            TenantContext.clear();
        }
    }

    // TASK-132: dispara sob demanda o mesmo trabalho do WhatsAppDeferredSendJob (normalmente roda a
    // cada 15min, e só envia de fato dentro do horário comercial 8h-20h Brasília) — attemptSend()
    // já revalida a janela de horário internamente, então fora dela isso é um no-op seguro.
    @GetMapping("/execute-whatsapp-deferred-send")
    @Operation(summary = "Disparar reenvio de dispatches represados em PENDING_HOURS_WINDOW sob demanda")
    public Map<String, Object> executeWhatsAppDeferredSendJob() {
        TenantContext.setSystemContext();
        try {
            List<BusinessWhatsAppDispatch> pending =
                    dispatchRepository.findAllByStatus(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW);
            for (BusinessWhatsAppDispatch dispatch : pending) {
                whatsAppNotificationService.attemptSend(dispatch);
            }
            return Map.of("candidatesProcessed", pending.size());
        } finally {
            TenantContext.clear();
        }
    }

}
