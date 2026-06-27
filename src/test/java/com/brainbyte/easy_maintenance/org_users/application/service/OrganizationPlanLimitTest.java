package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationPlanLimitTest {

    @Mock OrganizationRepository repository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingSubscriptionService billingSubscriptionService;
    @Mock BillingPlanRepository billingPlanRepository;
    @Mock BillingAccountRepository billingAccountRepository;
    @Mock UserRepository userRepository;
    @Mock AffiliateService affiliateService;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock UserOrganizationRepository userOrganizationRepository;

    @InjectMocks OrganizationsService service;

    private static final Long USER_ID = 42L;

    private BillingSubscriptionItem subItemWithMaxOrgs(int maxOrganizations) {
        BillingPlan plan = BillingPlan.builder().code("FREE").name("Free").priceCents(0).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxOrganizations(maxOrganizations).build());
        return BillingSubscriptionItem.builder().plan(plan).build();
    }

    private void stubUserSubscription(BillingSubscriptionItem subItem) {
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(
                eq(BillingSubscriptionItemSourceType.USER), eq(USER_ID.toString())))
                .thenReturn(Optional.of(subItem));
    }

    private void stubNoUserSubscription() {
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(
                eq(BillingSubscriptionItemSourceType.USER), eq(USER_ID.toString())))
                .thenReturn(Optional.empty());
    }

    // ── sem assinatura → skip (caso onboarding) ──────────────────────────────

    @Test
    void validateOrgLimit_skipsValidation_whenNoSubscriptionFound() {
        stubNoUserSubscription();

        assertThatCode(() -> service.validateOrgLimit(USER_ID))
                .doesNotThrowAnyException();

        verifyNoInteractions(userOrganizationRepository);
    }

    // ── maxOrganizations: erro quando no limite ───────────────────────────────

    @Test
    void validateOrgLimit_throwsRuleException_whenOrgCountEqualsMaxOrganizations() {
        stubUserSubscription(subItemWithMaxOrgs(2));
        when(userOrganizationRepository.countByUserId(USER_ID)).thenReturn(2L);

        assertThatThrownBy(() -> service.validateOrgLimit(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de organizações atingido")
                .hasMessageContaining("2/2");
    }

    @Test
    void validateOrgLimit_throwsRuleException_whenOrgCountExceedsMaxOrganizations() {
        stubUserSubscription(subItemWithMaxOrgs(1));
        when(userOrganizationRepository.countByUserId(USER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.validateOrgLimit(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de organizações atingido");
    }

    // ── maxOrganizations: permite quando abaixo do limite ────────────────────

    @Test
    void validateOrgLimit_allowsCreation_whenOrgCountBelowMaxOrganizations() {
        stubUserSubscription(subItemWithMaxOrgs(3));
        when(userOrganizationRepository.countByUserId(USER_ID)).thenReturn(1L);

        assertThatCode(() -> service.validateOrgLimit(USER_ID))
                .doesNotThrowAnyException();
    }

    // ── maxOrganizations=0 significa ilimitado ────────────────────────────────

    @Test
    void validateOrgLimit_allowsCreation_whenMaxOrganizationsIsZero_treatedAsUnlimited() {
        stubUserSubscription(subItemWithMaxOrgs(0));

        assertThatCode(() -> service.validateOrgLimit(USER_ID))
                .doesNotThrowAnyException();

        verify(userOrganizationRepository, never()).countByUserId(any());
    }
}
