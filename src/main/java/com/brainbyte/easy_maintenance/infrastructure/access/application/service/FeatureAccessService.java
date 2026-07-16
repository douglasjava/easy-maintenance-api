package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.ai.application.service.AiCreditService;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response.*;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    public static final String USER_INACTIVE_MSG = "Sua assinatura de usuário está inativa. Você ainda pode acessar os dados existentes, mas algumas operações da conta não estão permitidas.";
    public static final String USER_FULL_ACCESS_MSG = "Acesso total à conta.";
    public static final String USER_TRIAL_EXPIRED_MSG = "Seu período de trial encerrou. Assine um plano para continuar.";
    public static final String USER_MEMBER_MSG = "Você é membro de uma equipe. Seu acesso é gerenciado pelo titular da conta.";
    public static final String ORG_INACTIVE_MSG = "A assinatura desta organização está inativa. Você ainda pode visualizar os dados existentes, mas as operações de gravação não são permitidas.";
    public static final String ORG_FULL_ACCESS_MSG = "Acesso total à organização.";
    public static final String ORG_TRIAL_EXPIRED_MSG = "Trial expirado. Visualização apenas.";

    private final SubscriptionAccessService subscriptionAccessService;
    private final AuthenticationService authenticationService;
    private final OrganizationRepository organizationRepository;
    private final BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    private final MaintenanceItemRepository maintenanceItemRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final AiCreditService aiCreditService;
    private final MaintenanceAttachmentRepository maintenanceAttachmentRepository;

    public AccessContextResponse getAccessContext() {
        User user = authenticationService.getCurrentUser();

        log.info("user: {}", user.getName());

        AccountAccessResponse accountAccess = buildAccountAccess(user.getId(), user.getRole());

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

    private AccountAccessResponse buildAccountAccess(Long userId, Role userRole) {
        // Team members (non-ADMIN role) derive account access from the current org's subscription
        if (userRole != null && userRole != Role.ADMIN) {
            return buildMemberAccountAccess(userId);
        }

        // Account owners use their personal subscription
        Optional<BillingSubscriptionItem> subscriptionItem = subscriptionAccessService.getUserSubscriptionItem(userId);
        SubscriptionStatus rawStatus = subscriptionItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        Instant periodEnd = subscriptionItem.map(item -> item.getBillingSubscription().getCurrentPeriodEnd()).orElse(null);
        BillingPlan plan = subscriptionItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        SubscriptionStatus effectiveStatus = SubscriptionAccessService.resolveEffectiveStatus(rawStatus, periodEnd);
        AccessMode mode = subscriptionAccessService.resolveUserAccessMode(userId);

        // Populate trialExpiresAt whenever the underlying status is TRIAL (active or expired)
        Instant trialExpiresAt = (rawStatus == SubscriptionStatus.TRIAL) ? periodEnd : null;

        BillingPlanFeatures accountFeatures = billingPlanFeaturesHelper.parse(plan);

        return AccountAccessResponse.builder()
                .subscriptionStatus(effectiveStatus != null ? effectiveStatus.name() : "NONE")
                .accessMode(mode)
                .message(resolveAccountMessage(effectiveStatus, mode))
                .plan(mapPlan(plan))
                .features(accountFeatures)
                .permissions(buildAccountPermissions(mode))
                .trialExpiresAt(trialExpiresAt)
                .aiCreditsUsed(aiCreditService.getCreditsUsedThisMonth(userId))
                .aiCreditsLimit(accountFeatures.getAiMonthlyCredits())
                .build();
    }

    private AccountAccessResponse buildMemberAccountAccess(Long userId) {
        String orgCode = TenantContext.get().orElse(null);

        if (orgCode == null) {
            BillingPlanFeatures emptyFeatures = billingPlanFeaturesHelper.parse(null);
            return AccountAccessResponse.builder()
                    .subscriptionStatus("NONE")
                    .accessMode(AccessMode.READ_ONLY)
                    .message(USER_INACTIVE_MSG)
                    .features(emptyFeatures)
                    .permissions(buildMemberPermissions())
                    .aiCreditsUsed(aiCreditService.getCreditsUsedThisMonth(userId))
                    .aiCreditsLimit(0)
                    .build();
        }

        Optional<BillingSubscriptionItem> orgItem = subscriptionAccessService.getOrganizationSubscriptionItem(orgCode);
        SubscriptionStatus rawStatus = orgItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        Instant periodEnd = orgItem.map(item -> item.getBillingSubscription().getCurrentPeriodEnd()).orElse(null);
        BillingPlan orgPlan = orgItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        SubscriptionStatus effectiveStatus = SubscriptionAccessService.resolveEffectiveStatus(rawStatus, periodEnd);
        AccessMode mode = subscriptionAccessService.resolveOrganizationAccessMode(orgCode);

        BillingPlanFeatures memberFeatures = billingPlanFeaturesHelper.parse(orgPlan);

        boolean fullAccess = mode == AccessMode.FULL_ACCESS;
        String displayStatus = fullAccess
                ? SubscriptionStatus.MEMBER.name()
                : (effectiveStatus != null ? effectiveStatus.name() : "NONE");
        String message = fullAccess ? USER_MEMBER_MSG : resolveAccountMessage(effectiveStatus, mode);
        Instant trialExpiresAt = rawStatus == SubscriptionStatus.TRIAL ? periodEnd : null;

        return AccountAccessResponse.builder()
                .subscriptionStatus(displayStatus)
                .accessMode(mode)
                .message(message)
                .plan(fullAccess ? mapPlan(orgPlan) : null)
                .features(memberFeatures)
                .permissions(buildMemberPermissions())
                .trialExpiresAt(trialExpiresAt)
                .aiCreditsUsed(aiCreditService.getCreditsUsedThisMonth(userId))
                .aiCreditsLimit(memberFeatures.getAiMonthlyCredits())
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

    private AccountPermissionsResponse buildMemberPermissions() {
        return AccountPermissionsResponse.builder()
                .canViewOrganizations(true)
                .canCreateOrganization(false)
                .canManageOwnBilling(false)
                .build();
    }

    private OrganizationAccessResponse buildOrganizationAccess(Organization org) {
        Optional<BillingSubscriptionItem> subscriptionItem = subscriptionAccessService.getOrganizationSubscriptionItem(org.getCode());
        SubscriptionStatus rawStatus = subscriptionItem.map(item -> item.getBillingSubscription().getStatus()).orElse(null);
        Instant periodEnd = subscriptionItem.map(item -> item.getBillingSubscription().getCurrentPeriodEnd()).orElse(null);
        BillingPlan plan = subscriptionItem.map(BillingSubscriptionItem::getPlan).orElse(null);

        SubscriptionStatus effectiveStatus = SubscriptionAccessService.resolveEffectiveStatus(rawStatus, periodEnd);
        AccessMode mode = subscriptionAccessService.resolveOrganizationAccessMode(org.getCode());

        BillingPlanFeatures features = billingPlanFeaturesHelper.parse(plan);

        // Usage counts are only computed for the current tenant (TenantContext must match this org).
        // This prevents incorrect results from the TenantFilterAspect when iterating multiple orgs.
        OrganizationUsageResponse usage = TenantContext.get()
                .filter(tenant -> tenant.equals(org.getCode()))
                .map(tenant -> {
                    Instant startOfMonth = LocalDate.now().withDayOfMonth(1)
                            .atStartOfDay(ZoneId.systemDefault()).toInstant();
                    long uploadUsedBytes = maintenanceAttachmentRepository.sumSizeBytesByOrgSince(org.getCode(), startOfMonth);
                    return OrganizationUsageResponse.builder()
                            .currentItems(maintenanceItemRepository.countByOrganizationCode(org.getCode()))
                            .currentUsers(userOrganizationRepository.countByOrganizationCode(org.getCode()))
                            .uploadUsedMb(uploadUsedBytes / (1024L * 1024L))
                            .uploadLimitMb(features.getMaxMonthlyUploadsMb())
                            .build();
                })
                .orElse(null);

        // EPIC-014/TASK-111: maxItems é um pool compartilhado entre todas as organizações da
        // conta, não um teto por organização. usage.currentItems acima é o uso REAL só desta
        // organização (informativo); o bloqueio de criação precisa comparar contra o total da
        // conta inteira. Usa TenantContext.runCrossOrg pois essa soma cruza múltiplas
        // organizações e o TenantFilterAspect zeraria a contagem de qualquer uma que não seja a
        // ativa na sessão (ver TASK-120).
        boolean itemLimitReached = subscriptionItem.isPresent()
                && features.getMaxItems() > 0
                && countAccountWideItems(subscriptionItem.get()) >= features.getMaxItems();

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

    // EPIC-014/TASK-111: soma itens de todas as organizações da mesma BillingSubscription
    // (conta) — mesmo padrão usado em MaintenanceItemService.validateItemLimit.
    private long countAccountWideItems(BillingSubscriptionItem orgSubscriptionItem) {
        List<BillingSubscriptionItem> siblingItems = billingSubscriptionItemRepository
                .findAllByBillingSubscriptionId(orgSubscriptionItem.getBillingSubscription().getId());

        List<String> orgCodesInAccount = siblingItems.stream()
                .filter(item -> item.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION)
                .map(BillingSubscriptionItem::getSourceId)
                .toList();

        if (orgCodesInAccount.isEmpty()) {
            return 0L;
        }

        return TenantContext.runCrossOrg(() -> maintenanceItemRepository.countByOrganizationCodeIn(orgCodesInAccount));
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
