package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.ExternalCustomerSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalCustomerSyncJob {

    private final ExternalCustomerSyncService jobService;

    // Padrão: 6h da manhã — após todos os outros jobs de billing (trial 1:15, ciclo 2:00, bloqueio 3:00, cancelamento 4:00, notificação 5:00)
    @Scheduled(cron = "${billing.customer-sync.cron:0 0 6 * * *}")
    @SchedulerLock(name = "ExternalCustomerSyncJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void run() {
        log.info("[ExternalCustomerSyncJob] Lock adquirido. Iniciando sincronização de externalCustomerId.");
        jobService.syncMissingExternalCustomerIds();
        log.info("[ExternalCustomerSyncJob] Execução concluída.");
    }
}
