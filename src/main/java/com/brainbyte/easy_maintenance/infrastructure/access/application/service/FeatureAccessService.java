package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response.*;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    public static final String USER_INACTIVE_MSG = "Sua assinatura de usuário está inativa. Você ainda pode acessar os dados existentes, mas algumas operações da conta não estão permitidas.";
    public static final String USER_FULL_ACCESS_MSG = "Acesso total à conta.";
    public static final String USER_TRIAL_EXPIRED_MSG = "Seu período de trial encerrou. Assine um plano para continuar.";
    public static final String ORG_INACTIVE_MSG = "A assinatura desta organização está inativa. Você ainda pode visualizar os dados existentes, mas as operações de gravação não são permitidas.";
    public static final String ORG_FULL_ACCESS_MSG = "Acesso total à organização.";
    public static final String ORG_TRIAL_EXPIRED_MSG = "Trial expirado. Visualização apenas.";

    private final SubscriptionAccessService subscriptionAccessService;
    private final AuthenticationService authenticationService;
    private final OrganizationRepository organizationRepository;
    private final BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    private final MaintenanceItemRepository maintenanceItemRepository;
    private final UserOrganizationRepository userOrganizationRepository;

    public AccessContextResponse getAccessContext() {
        User user = authenticationService.getCurrentUser();

        AccountAccessResponse accountAccess = buildAccountAccess(user.getId());

        List<OrganizationAccessResponse> organizationsAccess = organizationRepository.findAllByUserId(user.getId())
                .stream()
                .map(this::buildOrganizationAccess)
                .toList();

        return AccessContextResponse.builder()
                .user(AccessContextResponse.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .build())
                .accountAccess(accountAccess)
                .organizationsAccess(organizationsAccess)
                .build();
    }

    private AccountAccessResponse buildAccountAccess(Long userId) {
        Optional<BillingSubscriptionItem> subscriptionItem = subscriptionAccessService.getUserSubscriptionItem(userId);
        SubscriptionStatus rawStatus = subscriptionItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        Instant periodEnd = subscriptionItem.map(item -> item.getBillingSubscription().getCurrentPeriodEnd()).orElse(null);
        BillingPlan plan = subscriptionItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        SubscriptionStatus effectiveStatus = SubscriptionAccessService.resolveEffectiveStatus(rawStatus, periodEnd);
        AccessMode mode = subscriptionAccessService.resolveUserAccessMode(userId);

        // Populate trialExpiresAt whenever the underlying status is TRIAL (active or expired)
        Instant trialExpiresAt = (rawStatus == SubscriptionStatus.TRIAL) ? periodEnd : null;

        return AccountAccessResponse.builder()
                .subscriptionStatus(effectiveStatus != null ? effectiveStatus.name() : "NONE")
                .accessMode(mode)
                .message(resolveAccountMessage(effectiveStatus, mode))
                .plan(mapPlan(plan))
                .features(billingPlanFeaturesHelper.parse(plan))
                .permissions(buildAccountPermissions(mode))
                .trialExpiresAt(trialExpiresAt)
                .build();
    }

    private static String resolveAccountMessage(SubscriptionStatus effectiveStatus, AccessMode mode) {
        if (effectiveStatus == SubscriptionStatus.TRIAL_EXPIRED) return USER_TRIAL_EXPIRED_MSG;
        return mode != AccessMode.FULL_ACCESS ? USER_INACTIVE_MSG : USER_FULL_ACCESS_MSG;
    }

    private AccountPermissionsResponse buildAccountPermissions(AccessMode mode) {
        boolean fullAccess = mode == AccessMode.FULL_ACCESS;
        return AccountPermissionsResponse.builder()
                .canViewOrganizations(true)
                .canCreateOrganization(fullAccess)
                .canManageOwnBilling(true)
                .build();
    }

    private OrganizationAccessResponse buildOrganizationAccess(Organization org) {
        Optional<BillingSubscriptionItem> subscriptionItem = subscriptionAccessService.getOrganizationSubscriptionItem(org.getCode());
        SubscriptionStatus rawStatus = subscriptionItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        Instant periodEnd = subscriptionItem.map(item -> item.getBillingSubscription().getCurrentPeriodEnd()).orElse(null);
        BillingPlan plan = subscriptionItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        SubscriptionStatus effectiveStatus = SubscriptionAccessService.resolveEffectiveStatus(rawStatus, periodEnd);
        AccessMode mode = subscriptionAccessService.resolveOrganizationAccessMode(org.getCode());

        // Usage counts are only computed for the current tenant (TenantContext must match this org).
        // This prevents incorrect results from the TenantFilterAspect when iterating multiple orgs.
        OrganizationUsageResponse usage = TenantContext.get()
                .filter(tenant -> tenant.equals(org.getCode()))
                .map(tenant -> OrganizationUsageResponse.builder()
                        .currentItems(maintenanceItemRepository.countByOrganizationCode(org.getCode()))
                        .currentUsers(userOrganizationRepository.countByOrganizationCode(org.getCode()))
                        .build())
                .orElse(null);

        BillingPlanFeatures features = billingPlanFeaturesHelper.parse(plan);
        boolean itemLimitReached = usage != null
                && features.getMaxItems() > 0
                && usage.getCurrentItems() >= features.getMaxItems();

        return OrganizationAccessResponse.builder()
                .organizationCode(org.getCode())
                .organizationName(org.getName())
                .subscriptionStatus(effectiveStatus != null ? effectiveStatus.name() : "NONE")
                .accessMode(mode)
                .message(resolveOrganizationMessage(effectiveStatus, mode))
                .plan(mapPlan(plan))
                .features(features)
                .permissions(buildOrganizationPermissions(mode, itemLimitReached))
                .currentUsage(usage)
                .build();
    }

    private static String resolveOrganizationMessage(SubscriptionStatus effectiveStatus, AccessMode mode) {
        if (effectiveStatus == SubscriptionStatus.TRIAL_EXPIRED) return ORG_TRIAL_EXPIRED_MSG;
        return mode != AccessMode.FULL_ACCESS ? ORG_INACTIVE_MSG : ORG_FULL_ACCESS_MSG;
    }

    private PlanSummaryResponse mapPlan(BillingPlan plan) {
        if (plan == null) return null;
        return PlanSummaryResponse.builder()
                .code(plan.getCode())
                .name(plan.getName())
                .build();
    }

    private OrganizationPermissionsResponse buildOrganizationPermissions(AccessMode mode, boolean itemLimitReached) {
        boolean fullAccess = mode == AccessMode.FULL_ACCESS;
        return OrganizationPermissionsResponse.builder()
                .canReadDashboard(true)
                .canCreateItem(fullAccess && !itemLimitReached)
                .canEditItem(fullAccess)
                .canDeleteItem(fullAccess)
                .canRegisterMaintenance(fullAccess)
                .canManageOrganizationUsers(fullAccess)
                .canUpdateOrganization(fullAccess)
                .canManageOrganizationBilling(true)
                .build();
    }
}
