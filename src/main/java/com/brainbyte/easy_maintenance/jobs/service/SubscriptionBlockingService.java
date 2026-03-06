package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBlockingService {

    private final BillingSubscriptionRepository billingSubscriptionRepository;

    @Value("${billing.blocking.days-after-due:3}")
    private int daysAfterDue;

    @Transactional
    public void executeBlockingJob() {
        log.info("[SubscriptionBlocking] Iniciando job de bloqueio de assinaturas.");

        Instant limitDate = Instant.now().minus(Duration.ofDays(daysAfterDue));

        log.debug("[SubscriptionBlocking] Buscando assinaturas financeiras elegíveis para bloqueio (vencimento <= {})", limitDate);

        var eligibleBillingSubs = billingSubscriptionRepository.findEligibleForBlocking(limitDate);

        log.info("[SubscriptionBlocking] Encontradas {} assinaturas financeiras para bloquear.", eligibleBillingSubs.size());

        eligibleBillingSubs.forEach(bs -> {
            try {

                if (bs.getStatus() != SubscriptionStatus.BLOCKED) {
                    log.info("[SubscriptionBlocking] Bloqueando contrato financeiro {} (Payer: {})", bs.getId(), bs.getBillingAccount().getUser().getId());

                    // 1. Bloquear contrato financeiro central
                    bs.block();
                    billingSubscriptionRepository.save(bs);
                    
                    log.info("[SubscriptionBlocking] BillingSubscription {} bloqueada com sucesso.", bs.getId());
                }

            } catch (Exception e) {
                log.error("[SubscriptionBlocking] Erro ao bloquear contrato financeiro {}: {}", bs.getId(), e.getMessage());
            }
        });

        log.info("[SubscriptionBlocking] Job de bloqueio finalizado.");
    }
}
