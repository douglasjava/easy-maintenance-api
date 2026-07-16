package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.ai.application.service.AiCreditService;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * EPIC-014 / TASK-111 (bugfix) — GET /me/access-context.organizationsAccess[].permissions.canCreateItem
 * precisa refletir o pool compartilhado de itens da conta (soma entre todas as organizações),
 * não a contagem isolada da organização sendo consultada. Antes desta correção, o botão "+ Novo
 * Item" no frontend continuava habilitado mesmo com o pool da conta esgotado, sempre que a
 * organização ativa isoladamente tivesse poucos itens.
 */
@ExtendWith(MockitoExtension.class)
class FeatureAccessServiceItemLimitTest {

    @Mock SubscriptionAccessService subscriptionAccessService;
    @Mock AuthenticationService authenticationService;
    @Mock OrganizationRepository organizationRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock MaintenanceItemRepository maintenanceItemRepository;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock AiCreditService aiCreditService;
    @Mock MaintenanceAttachmentRepository maintenanceAttachmentRepository;

    @InjectMocks FeatureAccessService service;

    private static final Long USER_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 5L;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(USER_ID).role(Role.ADMIN).build();
        when(authenticationService.getCurrentUser()).thenReturn(user);

        // buildAccountAccess (caminho do dono) não é o alvo deste teste — stubs mínimos leniente.
        lenient().when(subscriptionAccessService.getUserSubscriptionItem(USER_ID)).thenReturn(Optional.empty());
        lenient().when(subscriptionAccessService.resolveUserAccessMode(USER_ID)).thenReturn(AccessMode.FULL_ACCESS);
        lenient().when(billingPlanFeaturesHelper.parse(null)).thenReturn(BillingPlanFeatures.builder().build());
        lenient().when(aiCreditService.getCreditsUsedThisMonth(USER_ID)).thenReturn(0);
    }

    private BillingSubscriptionItem orgItem(String orgCode, BillingSubscription subscription, BillingPlan plan) {
        return BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION)
                .sourceId(orgCode)
                .plan(plan)
                .build();
    }

    @Test
    void canCreateItem_isFalse_whenAccountWidePoolIsExhausted_evenIfThisOrgAloneIsBelowLimit() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingPlan plan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build();

        Organization org = Organization.builder().code("ORG-BRAIN").name("Brain").build();
        when(organizationRepository.findAllByUserId(USER_ID)).thenReturn(List.of(org));

        BillingSubscriptionItem thisOrgItem = orgItem("ORG-BRAIN", subscription, plan);
        BillingSubscriptionItem otherOrgItem = orgItem("ORG-SOFIA", subscription, plan);

        when(subscriptionAccessService.getOrganizationSubscriptionItem("ORG-BRAIN")).thenReturn(Optional.of(thisOrgItem));
        when(subscriptionAccessService.resolveOrganizationAccessMode("ORG-BRAIN")).thenReturn(AccessMode.FULL_ACCESS);
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(BillingPlanFeatures.builder().maxItems(20).build());
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(thisOrgItem, otherOrgItem));
        // Pool da conta (Brain + Sofia) esgotado em 20/20 — mesmo que Brain sozinha tenha só 4.
        when(maintenanceItemRepository.countByOrganizationCodeIn(List.of("ORG-BRAIN", "ORG-SOFIA"))).thenReturn(20L);

        var response = service.getAccessContext();

        var orgAccess = response.getOrganizationsAccess().get(0);
        assertThat(orgAccess.getPermissions().isCanCreateItem())
                .as("pool esgotado da conta deve bloquear criação mesmo com acesso total à organização")
                .isFalse();
    }

    @Test
    void canCreateItem_isTrue_whenAccountWidePoolHasRoom() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingPlan plan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build();

        Organization org = Organization.builder().code("ORG-BRAIN").name("Brain").build();
        when(organizationRepository.findAllByUserId(USER_ID)).thenReturn(List.of(org));

        BillingSubscriptionItem thisOrgItem = orgItem("ORG-BRAIN", subscription, plan);

        when(subscriptionAccessService.getOrganizationSubscriptionItem("ORG-BRAIN")).thenReturn(Optional.of(thisOrgItem));
        when(subscriptionAccessService.resolveOrganizationAccessMode("ORG-BRAIN")).thenReturn(AccessMode.FULL_ACCESS);
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(BillingPlanFeatures.builder().maxItems(20).build());
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(thisOrgItem));
        when(maintenanceItemRepository.countByOrganizationCodeIn(List.of("ORG-BRAIN"))).thenReturn(4L);

        var response = service.getAccessContext();

        var orgAccess = response.getOrganizationsAccess().get(0);
        assertThat(orgAccess.getPermissions().isCanCreateItem()).isTrue();
    }

    @Test
    void canCreateItem_isTrue_whenMaxItemsIsZero_treatedAsUnlimited() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingPlan plan = BillingPlan.builder().code("ENTERPRISE").name("Enterprise").priceCents(89900).build();

        Organization org = Organization.builder().code("ORG-BRAIN").name("Brain").build();
        when(organizationRepository.findAllByUserId(USER_ID)).thenReturn(List.of(org));

        BillingSubscriptionItem thisOrgItem = orgItem("ORG-BRAIN", subscription, plan);

        when(subscriptionAccessService.getOrganizationSubscriptionItem("ORG-BRAIN")).thenReturn(Optional.of(thisOrgItem));
        when(subscriptionAccessService.resolveOrganizationAccessMode("ORG-BRAIN")).thenReturn(AccessMode.FULL_ACCESS);
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(BillingPlanFeatures.builder().maxItems(0).build());

        var response = service.getAccessContext();

        var orgAccess = response.getOrganizationsAccess().get(0);
        assertThat(orgAccess.getPermissions().isCanCreateItem()).isTrue();
    }
}
