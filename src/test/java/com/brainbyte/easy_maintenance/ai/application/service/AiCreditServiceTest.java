package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.domain.AiMonthlyUsage;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiMonthlyUsageRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCreditServiceTest {

    @Mock
    private AiMonthlyUsageRepository usageRepository;

    @Mock
    private BillingSubscriptionItemRepository subscriptionItemRepository;

    @Mock
    private BillingPlanFeaturesHelper featuresHelper;

    @Mock
    private BillingSubscriptionItem subscriptionItem;

    @Mock
    private BillingPlan plan;

    @InjectMocks
    private AiCreditService service;

    private static final Long USER_ID = 42L;
    private static final String USAGE_MONTH = YearMonth.now().toString();

    @Test
    void shouldReturnCreditsUsed_whenUsageRecordExists() {
        when(usageRepository.findByUserIdAndUsageMonth(USER_ID, USAGE_MONTH))
                .thenReturn(Optional.of(AiMonthlyUsage.builder()
                        .userId(USER_ID).usageMonth(USAGE_MONTH).creditsUsed(150).build()));

        assertThat(service.getCreditsUsedThisMonth(USER_ID)).isEqualTo(150);
    }

    @Test
    void shouldReturnZero_whenNoUsageRecordExists() {
        when(usageRepository.findByUserIdAndUsageMonth(USER_ID, USAGE_MONTH))
                .thenReturn(Optional.empty());

        assertThat(service.getCreditsUsedThisMonth(USER_ID)).isEqualTo(0);
    }

    @Test
    void shouldValidate_whenUserHasAvailableCredits() {
        BillingPlanFeatures features = BillingPlanFeatures.builder().aiMonthlyCredits(1000).build();
        when(subscriptionItem.getPlan()).thenReturn(plan);
        when(featuresHelper.parse(plan)).thenReturn(features);
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), any()))
                .thenReturn(List.of(subscriptionItem));
        when(usageRepository.findByUserIdAndUsageMonth(USER_ID, USAGE_MONTH))
                .thenReturn(Optional.of(AiMonthlyUsage.builder()
                        .userId(USER_ID).usageMonth(USAGE_MONTH).creditsUsed(500).build()));

        // Should not throw
        service.validateHasCredits(USER_ID);
    }

    @Test
    void shouldThrowRuleException_whenMonthlyLimitReached() {
        BillingPlanFeatures features = BillingPlanFeatures.builder().aiMonthlyCredits(100).build();
        when(subscriptionItem.getPlan()).thenReturn(plan);
        when(featuresHelper.parse(plan)).thenReturn(features);
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), any()))
                .thenReturn(List.of(subscriptionItem));
        when(usageRepository.findByUserIdAndUsageMonth(USER_ID, USAGE_MONTH))
                .thenReturn(Optional.of(AiMonthlyUsage.builder()
                        .userId(USER_ID).usageMonth(USAGE_MONTH).creditsUsed(100).build()));

        assertThatThrownBy(() -> service.validateHasCredits(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Cota mensal");
    }

    @Test
    void shouldThrowRuleException_whenPlanHasNoAiCredits() {
        BillingPlanFeatures features = BillingPlanFeatures.builder().aiMonthlyCredits(0).build();
        when(subscriptionItem.getPlan()).thenReturn(plan);
        when(featuresHelper.parse(plan)).thenReturn(features);
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), any()))
                .thenReturn(List.of(subscriptionItem));

        assertThatThrownBy(() -> service.validateHasCredits(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("não disponível");
    }

    @Test
    void shouldThrowRuleException_whenNoSubscriptionFound() {
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.validateHasCredits(USER_ID))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void shouldDeductCredits_updatingExistingRecord() {
        AiMonthlyUsage existing = AiMonthlyUsage.builder()
                .id(1L).userId(USER_ID).usageMonth(USAGE_MONTH).creditsUsed(10).build();
        when(usageRepository.findByUserIdAndUsageMonth(USER_ID, USAGE_MONTH))
                .thenReturn(Optional.of(existing));

        service.deductCredits(USER_ID, 1);

        ArgumentCaptor<AiMonthlyUsage> captor = ArgumentCaptor.forClass(AiMonthlyUsage.class);
        verify(usageRepository).save(captor.capture());
        assertThat(captor.getValue().getCreditsUsed()).isEqualTo(11);
    }

    @Test
    void shouldDeductCredits_creatingNewRecord_whenNoneExists() {
        when(usageRepository.findByUserIdAndUsageMonth(USER_ID, USAGE_MONTH))
                .thenReturn(Optional.empty());

        service.deductCredits(USER_ID, 1);

        ArgumentCaptor<AiMonthlyUsage> captor = ArgumentCaptor.forClass(AiMonthlyUsage.class);
        verify(usageRepository).save(captor.capture());
        AiMonthlyUsage saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getUsageMonth()).isEqualTo(USAGE_MONTH);
        assertThat(saved.getCreditsUsed()).isEqualTo(1);
    }
}
