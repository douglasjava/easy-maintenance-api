package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.infrastructure.observability.service.JobHealthReporter;
import com.brainbyte.easy_maintenance.jobs.service.BillingReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingReconciliationJob {

    private final BillingReconciliationService reconciliationService;
    private final JobHealthReporter jobHealthReporter;

    @Scheduled(cron = "${billing.reconciliation.cron:0 0 3 * * *}")
    @SchedulerLock(name = "BillingReconciliationJob", lockAtMostFor = "PT1H", lockAtLeastFor = "PT5M")
    public void run() {
        log.info("[BillingReconciliationJob] Lock adquirido. Iniciando reconciliação Asaas vs estado local.");
        try {
            reconciliationService.reconcile();
            jobHealthReporter.markSuccess("billing_reconciliation");
            log.info("[BillingReconciliationJob] Execução concluída com sucesso.");
        } catch (Exception e) {
            log.error("[BillingReconciliationJob] Erro inesperado: {}", e.getMessage(), e);
        }
    }
}
