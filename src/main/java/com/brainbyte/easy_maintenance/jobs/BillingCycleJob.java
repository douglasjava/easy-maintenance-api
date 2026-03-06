package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.jobs.service.BillingCycleJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingCycleJob {

    private final BillingCycleJobService billingCycleJobService;

    @Scheduled(cron = "${billing.cycle.cron:0 30 2 * * *}") // Padrão: 2:30h da manhã
    public void run() {
        billingCycleJobService.executeCycleTurnJob();
    }
}
