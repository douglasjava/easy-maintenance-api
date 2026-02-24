package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.TrialExpirationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyTrialWatcher {

    private final TrialExpirationJobService jobService;

    // Executa 1x por dia às 01:15
    @Scheduled(cron = "0 15 1 * * *")
    public void run() {
        log.info("Running DailyTrialWatcher job");
        jobService.processTrialsExpiringWithinDays(2);
    }
}
