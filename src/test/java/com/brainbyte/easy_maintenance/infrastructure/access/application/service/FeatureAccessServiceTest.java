package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.ai.application.service.AiCreditService;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response.AccountAccessResponse;
import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response.AccessContextResponse;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureAccessServiceTest {

    @Mock private SubscriptionAccessService subscriptionAccessService;
    @Mock private AuthenticationService authenticationService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock private MaintenanceItemRepository maintenanceItemRepository;
    @Mock private UserOrganizationRepository userOrganizationRepository;
    @Mock private AiCreditService aiCreditService;
    @Mock private MaintenanceAttachmentRepository maintenanceAttachmentRepository;

    @InjectMocks
    private FeatureAccessService featureAccessService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ──────────────────────────────────────────────────────────────
    // MEMBER path
    // ──────────────────────────────────────────────────────────────

    @Test
    void memberWithActiveOrg_shouldReturnMemberStatusAndFullAccess() {
        User member = User.builder().id(10L).name("João").role(Role.READER).build();
        when(authenticationService.getCurrentUser()).thenReturn(member);
        when(organizationRepository.findAllByUserId(10L)).thenReturn(List.of());

        TenantContext.set("ORG-ABC");

        BillingPlan plan = BillingPlan.builder().code("BASIC").name("Básico").featuresJson("{}").build();
        BillingSubscription subscription = BillingSubscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(Instant.now().plusSeconds(86400))
                .build();
        BillingSubscriptionItem orgItem = BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .plan(plan)
                .build();

        when(subscriptionAccessService.getOrganizationSubscriptionItem("ORG-ABC"))
                .thenReturn(Optional.of(orgItem));
        when(subscriptionAccessService.resolveOrganizationAccessMode("ORG-ABC"))
                .thenReturn(AccessMode.FULL_ACCESS);
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxUsers(5).aiMonthlyCredits(100).build());
        when(aiCreditService.getCreditsUsedThisMonth(10L)).thenReturn(3);

        AccessContextResponse result = featureAccessService.getAccessContext();
        AccountAccessResponse account = result.getAccountAccess();

        assertThat(account.getSubscriptionStatus()).isEqualTo("MEMBER");
        assertThat(account.getAccessMode()).isEqualTo(AccessMode.FULL_ACCESS);
        assertThat(account.getMessage()).isEqualTo(FeatureAccessService.USER_MEMBER_MSG);
        assertThat(account.getPermissions().isCanManageOwnBilling()).isFalse();
        assertThat(account.getPermissions().isCanCreateOrganization()).isFalse();
        assertThat(account.getPermissions().isCanViewOrganizations()).isTrue();
        assertThat(account.getAiCreditsUsed()).isEqualTo(3);
    }

    @Test
    void memberWithNoTenantContext_shouldReturnNoneAndReadOnly() {
        User member = User.builder().id(11L).name("Maria").role(Role.TECH).build();
        when(authenticationService.getCurrentUser()).thenReturn(member);
        when(organizationRepository.findAllByUserId(11L)).thenReturn(List.of());

        // TenantContext not set — simulates member with no org selected
        BillingPlanFeatures emptyFeatures = new BillingPlanFeatures();
        when(billingPlanFeaturesHelper.parse(null)).thenReturn(emptyFeatures);
        when(aiCreditService.getCreditsUsedThisMonth(11L)).thenReturn(0);

        AccessContextResponse result = featureAccessService.getAccessContext();
        AccountAccessResponse account = result.getAccountAccess();

        assertThat(account.getSubscriptionStatus()).isEqualTo("NONE");
        assertThat(account.getAccessMode()).isEqualTo(AccessMode.READ_ONLY);
        assertThat(account.getPermissions().isCanManageOwnBilling()).isFalse();
        assertThat(account.getAiCreditsLimit()).isEqualTo(0);
    }

    @Test
    void memberWithInactiveOrg_shouldReturnReadOnly() {
        User member = User.builder().id(12L).name("Carlos").role(Role.SYNDIC).build();
        when(authenticationService.getCurrentUser()).thenReturn(member);
        when(organizationRepository.findAllByUserId(12L)).thenReturn(List.of());

        TenantContext.set("ORG-INACTIVE");

        BillingSubscription inactiveSub = BillingSubscription.builder()
                .status(SubscriptionStatus.CANCELED)
                .currentPeriodEnd(Instant.now().minusSeconds(86400))
                .build();
        BillingSubscriptionItem orgItem = BillingSubscriptionItem.builder()
                .billingSubscription(inactiveSub)
                .plan(null)
                .build();

        when(subscriptionAccessService.getOrganizationSubscriptionItem("ORG-INACTIVE"))
                .thenReturn(Optional.of(orgItem));
        when(subscriptionAccessService.resolveOrganizationAccessMode("ORG-INACTIVE"))
                .thenReturn(AccessMode.READ_ONLY);
        when(billingPlanFeaturesHelper.parse(null)).thenReturn(new BillingPlanFeatures());
        when(aiCreditService.getCreditsUsedThisMonth(12L)).thenReturn(0);

        AccessContextResponse result = featureAccessService.getAccessContext();
        AccountAccessResponse account = result.getAccountAccess();

        assertThat(account.getSubscriptionStatus()).isEqualTo("CANCELED");
        assertThat(account.getAccessMode()).isEqualTo(AccessMode.READ_ONLY);
        assertThat(account.getPermissions().isCanManageOwnBilling()).isFalse();
    }

    // ──────────────────────────────────────────────────────────────
    // ADMIN (owner) path — regression
    // ──────────────────────────────────────────────────────────────

    @Test
    void adminWithActiveSubscription_shouldReturnActiveStatusAndFullAccess() {
        User admin = User.builder().id(1L).name("Dono").role(Role.ADMIN).build();
        when(authenticationService.getCurrentUser()).thenReturn(admin);
        when(organizationRepository.findAllByUserId(1L)).thenReturn(List.of());

        BillingPlan plan = BillingPlan.builder().code("PRO").name("Pro").featuresJson("{}").build();
        BillingSubscription subscription = BillingSubscription.builder()
                .status(SubscriptionStatus.ACTIVE)
                .currentPeriodEnd(Instant.now().plusSeconds(86400))
                .build();
        BillingSubscriptionItem userItem = BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .plan(plan)
                .build();

        when(subscriptionAccessService.getUserSubscriptionItem(1L)).thenReturn(Optional.of(userItem));
        when(subscriptionAccessService.resolveUserAccessMode(1L)).thenReturn(AccessMode.FULL_ACCESS);
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(new BillingPlanFeatures());
        when(aiCreditService.getCreditsUsedThisMonth(1L)).thenReturn(0);

        AccessContextResponse result = featureAccessService.getAccessContext();
        AccountAccessResponse account = result.getAccountAccess();

        assertThat(account.getSubscriptionStatus()).isEqualTo("ACTIVE");
        assertThat(account.getAccessMode()).isEqualTo(AccessMode.FULL_ACCESS);
        assertThat(account.getPermissions().isCanManageOwnBilling()).isTrue();
        assertThat(account.getPermissions().isCanCreateOrganization()).isTrue();
    }
}
