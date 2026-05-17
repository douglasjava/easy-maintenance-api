package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.PixRenewalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PixRenewalJob {

    private final PixRenewalService pixRenewalService;

    @Value("${billing.pix.renewal.days-ahead:5}")
    private int daysAhead;

    @Scheduled(cron = "${billing.pix.renewal.cron:0 30 1 * * *}")
    @SchedulerLock(name = "PixRenewalJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT15M")
    public void run() {
        log.info("[PixRenewalJob] Lock adquirido. Iniciando renovação de cobranças PIX (daysAhead={}).", daysAhead);
        try {
            pixRenewalService.processPixRenewals(daysAhead);
            log.info("[PixRenewalJob] Execução concluída com sucesso.");
        } catch (Exception e) {
            log.error("[PixRenewalJob] Erro inesperado durante execução do job: {}", e.getMessage(), e);
        }
    }
}
