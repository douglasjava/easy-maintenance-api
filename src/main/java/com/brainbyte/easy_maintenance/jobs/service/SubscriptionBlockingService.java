package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionBlockingService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final OrganizationSubscriptionRepository organizationSubscriptionRepository;

    @Value("${billing.blocking.days-after-due:3}")
    private int daysAfterDue;

    @Transactional
    public void executeBlockingJob() {
        log.info("[SubscriptionBlocking] Iniciando job de bloqueio de assinaturas.");

        Instant now = Instant.now();
        LocalDate limitDate = LocalDate.now().minusDays(daysAfterDue);

        log.debug("[SubscriptionBlocking] Buscando assinaturas elegíveis para bloqueio (vencimento <= {})", limitDate);

        var eligibleUserSubs = userSubscriptionRepository.findEligibleForBlocking(now, limitDate);
        var eligibleOrgSubs = organizationSubscriptionRepository.findEligibleForBlocking(now, limitDate);

        log.info("[SubscriptionBlocking] Encontradas {} assinaturas de usuários e {} assinaturas de organizações para bloquear.",
                eligibleUserSubs.size(), eligibleOrgSubs.size());

        eligibleUserSubs.forEach(sub -> {
            try {
                if (sub.getStatus() != SubscriptionStatus.BLOCKED) {
                    sub.setStatus(SubscriptionStatus.BLOCKED);
                    userSubscriptionRepository.save(sub);
                    log.info("[SubscriptionBlocking] Usuário {} bloqueado (Subscription ID: {})", 
                            sub.getUser().getId(), sub.getId());
                }
            } catch (Exception e) {
                log.error("[SubscriptionBlocking] Erro ao bloquear assinatura de usuário {}: {}", sub.getId(), e.getMessage());
            }
        });

        eligibleOrgSubs.forEach(sub -> {
            try {
                if (sub.getStatus() != SubscriptionStatus.BLOCKED) {
                    sub.setStatus(SubscriptionStatus.BLOCKED);
                    organizationSubscriptionRepository.save(sub);
                    log.info("[SubscriptionBlocking] Organização {} bloqueada (Subscription ID: {}, Payer: {})", 
                            sub.getOrganization().getId(), sub.getId(), sub.getPayer().getId());
                }
            } catch (Exception e) {
                log.error("[SubscriptionBlocking] Erro ao bloquear assinatura de organização {}: {}", sub.getId(), e.getMessage());
            }
        });

        log.info("[SubscriptionBlocking] Job de bloqueio finalizado.");
    }
}
