package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessEmailDispatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessEmailQuotaService {

    private final BusinessEmailDispatchRepository dispatchRepository;
    private final BillingSubscriptionItemRepository subscriptionItemRepository;
    private final BillingPlanFeaturesHelper featuresHelper;

    public boolean canSend(String organizationCode) {
        long limit = getEmailMonthlyLimit(organizationCode);
        if (limit <= 0) {
            log.warn("[Quota] Organização {} não possui limite de e-mail configurado ou limite é zero.", organizationCode);
            return false;
        }

        long sentCount = countSentInCurrentMonth(organizationCode);
        return sentCount < limit;
    }

    public void validateCanSend(String organizationCode) {
        if (!canSend(organizationCode)) {
            throw new RuntimeException("Cota mensal de e-mail atingida para a organização: " + organizationCode);
        }
    }

    public long countSentInCurrentMonth(String organizationCode) {
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return dispatchRepository.countSentInMonth(organizationCode, startOfMonth);
    }

    private long getEmailMonthlyLimit(String organizationCode) {
        List<BillingSubscriptionItem> items = subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of(organizationCode));

        if (items.isEmpty()) {
            log.warn("[Quota] Nenhuma assinatura ativa encontrada para a organização {}", organizationCode);
            return 0;
        }

        BillingSubscriptionItem item = items.getFirst();
        BillingPlanFeatures features = featuresHelper.parse(item.getPlan());
        return features.getEmailMonthlyLimit();
    }

}
