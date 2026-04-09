package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.TrialExpirationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTrialJob {

    private final TrialExpirationService jobService;

    // Executa 1x por dia às 01:15
    @Scheduled(cron = "0 15 1 * * *")
    @SchedulerLock(name = "DailyTrialJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT15M")
    public void run() {
        log.info("[DailyTrialJob] Lock adquirido. Iniciando execução.");
        jobService.processTrialsExpiringWithinDays(1);
        log.info("[DailyTrialJob] Execução concluída.");
    }
}
