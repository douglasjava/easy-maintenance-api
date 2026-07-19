package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.BusinessWhatsAppNotificationService;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * TASK-131: processa dispatches represados em {@code PENDING_HOURS_WINDOW} (fora do horário
 * comercial de 8h-20h Brasília no momento do disparo original) assim que a janela permitida
 * começar. Roda a cada 15 minutos mas só faz trabalho de verdade dentro do horário — fora dele é
 * um no-op barato (evita reprocessar a lista inteira sem necessidade).
 *
 * Mesmo padrão de {@link EmailRetryJob}: ShedLock + TenantContext.setSystemContext() (job roda em
 * background, sem X-Org-Id de request — sem isso as queries cross-org do
 * {@code BusinessWhatsAppNotificationService} não enxergariam nada).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppDeferredSendJob {

    private final BusinessWhatsAppDispatchRepository dispatchRepository;
    private final BusinessWhatsAppNotificationService whatsAppNotificationService;

    @Scheduled(cron = "${notification.whatsapp.deferred-send.cron:0 */15 * * * *}")
    @SchedulerLock(name = "WhatsAppDeferredSendJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT2M")
    public void run() {
        if (!whatsAppNotificationService.isWithinBusinessHours()) {
            log.debug("[WhatsAppDeferredSendJob] Fora do horário comercial — nada a fazer.");
            return;
        }

        TenantContext.setSystemContext();
        try {
            List<BusinessWhatsAppDispatch> pending =
                    dispatchRepository.findAllByStatus(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW);

            if (pending.isEmpty()) {
                log.debug("[WhatsAppDeferredSendJob] Nenhum dispatch represado para enviar.");
                return;
            }

            log.info("[WhatsAppDeferredSendJob] {} dispatch(es) represado(s) — tentando enviar agora.", pending.size());
            for (BusinessWhatsAppDispatch dispatch : pending) {
                try {
                    whatsAppNotificationService.attemptSend(dispatch);
                } catch (Exception e) {
                    log.error("[WhatsAppDeferredSendJob] Erro ao processar dispatch {}: {}", dispatch.getId(), e.getMessage());
                }
            }
            log.info("[WhatsAppDeferredSendJob] Job finalizado.");

        } finally {
            TenantContext.clear();
        }
    }
}
