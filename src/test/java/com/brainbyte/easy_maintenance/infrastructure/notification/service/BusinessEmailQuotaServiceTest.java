package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessEmailDispatchRepository;
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
class BusinessEmailQuotaServiceTest {

    @Mock BusinessEmailDispatchRepository dispatchRepository;
    @Mock BillingSubscriptionItemRepository subscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper featuresHelper;

    @InjectMocks BusinessEmailQuotaService service;

    private static final String ORG = "ORG-001";

    private void stubSubscriptionWithLimit(int emailMonthlyLimit) {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        BillingSubscriptionItem item = BillingSubscriptionItem.builder().plan(plan).build();
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of(item));
        when(featuresHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().emailMonthlyLimit(emailMonthlyLimit).build());
    }

    private void stubNoSubscription() {
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of());
    }

    private void stubSentCount(long count) {
        when(dispatchRepository.countSentInMonth(eq(ORG), any(Instant.class))).thenReturn(count);
    }

    // ── canSend: abaixo do limite ─────────────────────────────────────────────

    @Test
    void canSend_returnsTrue_whenBelowMonthlyLimit() {
        stubSubscriptionWithLimit(100);
        stubSentCount(50);

        assertThat(service.canSend(ORG)).isTrue();
    }

    // ── canSend: no limite ────────────────────────────────────────────────────

    @Test
    void canSend_returnsFalse_whenAtMonthlyLimit() {
        stubSubscriptionWithLimit(100);
        stubSentCount(100);

        assertThat(service.canSend(ORG)).isFalse();
    }

    // ── canSend: sem assinatura ───────────────────────────────────────────────

    @Test
    void canSend_returnsFalse_whenNoSubscription() {
        stubNoSubscription();

        assertThat(service.canSend(ORG)).isFalse();

        // limite=0 por ausência de assinatura → retorno antecipado antes do countSentInMonth
        verifyNoInteractions(dispatchRepository);
    }

    // ── canSend: limit=0 configurado no plano ────────────────────────────────

    @Test
    void canSend_returnsFalse_whenEmailLimitIsZero() {
        stubSubscriptionWithLimit(0);

        assertThat(service.canSend(ORG)).isFalse();

        verifyNoInteractions(dispatchRepository);
    }

    // ── validateCanSend: lança RuntimeException quando cota atingida ─────────

    @Test
    void validateCanSend_throwsRuntimeException_whenLimitReached() {
        stubSubscriptionWithLimit(50);
        stubSentCount(50);

        assertThatThrownBy(() -> service.validateCanSend(ORG))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Cota mensal de e-mail atingida");
    }
}
