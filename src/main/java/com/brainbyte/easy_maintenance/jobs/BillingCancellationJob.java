package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingCancellationJob {

    private final BillingSubscriptionService billingSubscriptionService;

    @Scheduled(cron = "${billing.cancellation.cron:0 0 4 * * *}") // Padrão: 4:00h da manhã
    public void run() {
        log.info("[BillingCancellationJob] Iniciando job de aplicação de cancelamentos.");
        try {
            billingSubscriptionService.processSubscriptionCycle();
        } catch (Exception e) {
            log.error("[BillingCancellationJob] Erro ao processar cancelamentos: {}", e.getMessage());
        }
        log.info("[BillingCancellationJob] Job de aplicação de cancelamentos finalizado.");
    }
}
