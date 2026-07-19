package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Espelha {@link BusinessEmailQuotaService} — quota mensal por conta, mas para o canal WhatsApp
 * (custo direto por mensagem, diferente de push/in-app). */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessWhatsAppQuotaService {

    private final BusinessWhatsAppDispatchRepository dispatchRepository;
    private final BillingSubscriptionItemRepository subscriptionItemRepository;
    private final BillingPlanFeaturesHelper featuresHelper;

    public boolean canSend(String organizationCode) {
        long limit = getWhatsappMonthlyLimit(organizationCode);
        if (limit <= 0) {
            log.warn("[WhatsAppQuota] Organização {} não possui limite de WhatsApp configurado ou limite é zero.",
                    organizationCode);
            return false;
        }

        long sentCount = countSentInCurrentMonth(organizationCode);
        return sentCount < limit;
    }

    public long countSentInCurrentMonth(String organizationCode) {
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return dispatchRepository.countSentInMonth(organizationCode, startOfMonth);
    }

    private long getWhatsappMonthlyLimit(String organizationCode) {
        List<BillingSubscriptionItem> items = subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of(organizationCode));

        if (items.isEmpty()) {
            log.warn("[WhatsAppQuota] Nenhuma assinatura ativa encontrada para a organização {}", organizationCode);
            return 0;
        }

        BillingSubscriptionItem item = items.getFirst();
        BillingPlanFeatures features = featuresHelper.parse(item.getPlan());
        return features.getWhatsappMonthlyLimit();
    }
}
