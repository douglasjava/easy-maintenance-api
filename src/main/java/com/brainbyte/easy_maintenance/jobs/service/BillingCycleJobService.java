package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingCycleJobService {

    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final BillingSubscriptionService billingSubscriptionService;

    @Transactional
    public void executeCycleTurnJob() {
        log.info("[BillingCycleJob] Iniciando job de virada de ciclo.");

        var now = Instant.now();
        var eligibleForPlanChange = billingSubscriptionItemRepository.findEligibleForPlanChange(now);

        log.info("[BillingCycleJob] Encontradas {} assinaturas com mudança de plano agendada.", eligibleForPlanChange.size());

        eligibleForPlanChange.forEach(bs -> {
            try {
                log.info("[BillingCycleJob] Aplicando planos agendados para assinatura {}", bs.getId());
                billingSubscriptionService.applyPendingPlans(bs);
            } catch (Exception e) {
                log.error("[BillingCycleJob] Erro ao aplicar planos para assinatura {}: {}", bs.getId(), e.getMessage());
            }
        });

        log.info("[BillingCycleJob] Job de virada de ciclo finalizado.");
    }
}
