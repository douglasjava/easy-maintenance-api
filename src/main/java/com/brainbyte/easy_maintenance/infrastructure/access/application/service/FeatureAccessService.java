package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response.*;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    public static final String USER_INACTIVE_MSG = "Sua assinatura de usuário está inativa. Você ainda pode acessar os dados existentes, mas algumas operações da conta não estão permitidas.";
    public static final String USER_FULL_ACCESS_MSG = "Acesso total à conta.";
    public static final String ORG_INACTIVE_MSG = "A assinatura desta organização está inativa. Você ainda pode visualizar os dados existentes, mas as operações de gravação não são permitidas.";
    public static final String ORG_FULL_ACCESS_MSG = "Acesso total à organização.";

    private final SubscriptionAccessService subscriptionAccessService;
    private final AuthenticationService authenticationService;
    private final OrganizationRepository organizationRepository;
    private final BillingPlanFeaturesHelper billingPlanFeaturesHelper;

    public AccessContextResponse getAccessContext() {
        User user = authenticationService.getCurrentUser();

        AccountAccessResponse accountAccess = buildAccountAccess(user.getId());

        List<OrganizationAccessResponse> organizationsAccess = organizationRepository.findAllByUserId(user.getId())
                .stream()
                .map(org -> buildOrganizationAccess(org))
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
        AccessMode mode = subscriptionAccessService.resolveUserAccessMode(userId);
        Optional<BillingSubscriptionItem> subscriptionItem = subscriptionAccessService.getUserSubscriptionItem(userId);
        SubscriptionStatus status = subscriptionItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        BillingPlan plan = subscriptionItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        return AccountAccessResponse.builder()
                .subscriptionStatus(status != null ? status.name() : "NONE")
                .accessMode(mode)
                .message(mode != AccessMode.FULL_ACCESS ? USER_INACTIVE_MSG : USER_FULL_ACCESS_MSG)
                .plan(mapPlan(plan))
                .features(billingPlanFeaturesHelper.parse(plan))
                .permissions(buildAccountPermissions(mode))
                .build();
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
        AccessMode mode = subscriptionAccessService.resolveOrganizationAccessMode(org.getCode());
        Optional<BillingSubscriptionItem> subscriptionItem = subscriptionAccessService.getOrganizationSubscriptionItem(org.getCode());
        SubscriptionStatus status = subscriptionItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        BillingPlan plan = subscriptionItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        return OrganizationAccessResponse.builder()
                .organizationCode(org.getCode())
                .organizationName(org.getName())
                .subscriptionStatus(status != null ? status.name() : "NONE")
                .accessMode(mode)
                .message(mode != AccessMode.FULL_ACCESS ? ORG_INACTIVE_MSG : ORG_FULL_ACCESS_MSG)
                .plan(mapPlan(plan))
                .features(billingPlanFeaturesHelper.parse(plan))
                .permissions(buildOrganizationPermissions(mode))
                .build();
    }

    private PlanSummaryResponse mapPlan(BillingPlan plan) {
        if (plan == null) return null;
        return PlanSummaryResponse.builder()
                .code(plan.getCode())
                .name(plan.getName())
                .build();
    }

    private OrganizationPermissionsResponse buildOrganizationPermissions(AccessMode mode) {
        boolean fullAccess = mode == AccessMode.FULL_ACCESS;
        return OrganizationPermissionsResponse.builder()
                .canReadDashboard(true)
                .canCreateItem(fullAccess)
                .canEditItem(fullAccess)
                .canDeleteItem(fullAccess)
                .canRegisterMaintenance(fullAccess)
                .canManageOrganizationUsers(fullAccess)
                .canUpdateOrganization(fullAccess)
                .canManageOrganizationBilling(true)
                .build();
    }
}
