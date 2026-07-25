package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessWhatsAppQuotaServiceTest {

    @Mock BusinessWhatsAppDispatchRepository dispatchRepository;
    @Mock BillingSubscriptionItemRepository subscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper featuresHelper;

    @InjectMocks BusinessWhatsAppQuotaService service;

    private static final String ORG = "ORG-001";

    private void stubSubscriptionWithLimit(int whatsappMonthlyLimit) {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        BillingSubscriptionItem item = BillingSubscriptionItem.builder().plan(plan).build();
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of(item));
        when(featuresHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().whatsappMonthlyLimit(whatsappMonthlyLimit).build());
    }

    private void stubNoSubscription() {
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of());
    }

    private void stubSentCount(long count) {
        when(dispatchRepository.countSentInMonth(eq(ORG), any(Instant.class))).thenReturn(count);
    }

    @Test
    void canSend_returnsTrue_whenBelowMonthlyLimit() {
        stubSubscriptionWithLimit(30);
        stubSentCount(10);

        assertThat(service.canSend(ORG)).isTrue();
    }

    @Test
    void canSend_returnsFalse_whenAtMonthlyLimit() {
        stubSubscriptionWithLimit(30);
        stubSentCount(30);

        assertThat(service.canSend(ORG)).isFalse();
    }

    @Test
    void canSend_returnsFalse_whenNoSubscription() {
        stubNoSubscription();

        assertThat(service.canSend(ORG)).isFalse();

        verifyNoInteractions(dispatchRepository);
    }

    @Test
    void canSend_returnsFalse_whenWhatsappLimitIsZero() {
        stubSubscriptionWithLimit(0);

        assertThat(service.canSend(ORG)).isFalse();

        verifyNoInteractions(dispatchRepository);
    }
}
