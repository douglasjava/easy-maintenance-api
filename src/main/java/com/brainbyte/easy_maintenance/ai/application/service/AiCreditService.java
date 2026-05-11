package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.domain.AiMonthlyUsage;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiMonthlyUsageRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiCreditService {

    private final AiMonthlyUsageRepository usageRepository;
    private final BillingSubscriptionItemRepository subscriptionItemRepository;
    private final BillingPlanFeaturesHelper featuresHelper;

    public int getMonthlyLimit(Long userId) {
        List<BillingSubscriptionItem> items = subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.USER, List.of(userId.toString()));
        if (items.isEmpty()) {
            log.warn("[AiCredit] Nenhuma assinatura ativa encontrada para userId={}", userId);
            return 0;
        }
        BillingPlanFeatures features = featuresHelper.parse(items.getFirst().getPlan());
        return features.getAiMonthlyCredits();
    }

    public int getCreditsUsedThisMonth(Long userId) {
        String usageMonth = YearMonth.now().toString();
        return usageRepository.findByUserIdAndUsageMonth(userId, usageMonth)
                .map(AiMonthlyUsage::getCreditsUsed)
                .orElse(0);
    }

    public void validateHasCredits(Long userId) {
        int limit = getMonthlyLimit(userId);
        if (limit <= 0) {
            throw new RuleException("Funcionalidade de IA não disponível no plano atual");
        }
        int used = getCreditsUsedThisMonth(userId);
        if (used >= limit) {
            throw new RuleException(
                    String.format("Cota mensal de créditos de IA atingida (%d/%d). Aguarde o próximo ciclo ou faça upgrade do plano.", used, limit));
        }
    }

    @Transactional
    public void deductCredits(Long userId, int amount) {
        String usageMonth = YearMonth.now().toString();
        AiMonthlyUsage usage = usageRepository.findByUserIdAndUsageMonth(userId, usageMonth)
                .orElseGet(() -> AiMonthlyUsage.builder()
                        .userId(userId)
                        .usageMonth(usageMonth)
                        .creditsUsed(0)
                        .build());
        usage.setCreditsUsed(usage.getCreditsUsed() + amount);
        usageRepository.save(usage);
        log.info("[AiCredit] Deducted {} credit(s) for userId={} — total this month: {}", amount, userId, usage.getCreditsUsed());
    }
}
