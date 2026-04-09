package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.SubscriptionBlockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionBlockingJob {

    private final SubscriptionBlockingService subscriptionBlockingService;

    @Scheduled(cron = "${billing.blocking.cron:0 0 3 * * *}") // Padrão: 3h da manhã
    @SchedulerLock(name = "SubscriptionBlockingJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT15M")
    public void run() {
        log.info("[SubscriptionBlockingJob] Lock adquirido. Iniciando execução diária do job de bloqueio.");
        try {
            subscriptionBlockingService.executeBlockingJob();
            log.info("[SubscriptionBlockingJob] Execução concluída com sucesso.");
        } catch (Exception e) {
            log.error("[SubscriptionBlockingJob] Erro inesperado durante execução do job: {}", e.getMessage(), e);
        }
    }
}
