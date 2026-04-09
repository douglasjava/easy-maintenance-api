package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.BillingCycleJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingCycleJob {

    private final BillingCycleJobService billingCycleJobService;

    @Scheduled(cron = "${billing.cycle.cron:0 0 2 * * *}") // 2:00h da manhã
    @SchedulerLock(name = "BillingCycleJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT15M")
    public void run() {
        log.info("[BillingCycleJob] Lock adquirido. Iniciando execução do ciclo de billing.");
        try {
            billingCycleJobService.executeCycleTurnJob();
            log.info("[BillingCycleJob] Execução concluída com sucesso.");
        } catch (Exception e) {
            log.error("[BillingCycleJob] Erro inesperado durante execução do job: {}", e.getMessage(), e);
        }
    }
}
