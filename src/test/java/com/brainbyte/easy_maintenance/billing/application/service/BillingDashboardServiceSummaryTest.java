package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * EPIC-014 / TASK-115 — GET /me/billing/summary passa a expor uso do plano único por conta
 * (organizações, usuários e itens dentro do pool compartilhado) para a tela /billing consolidada.
 */
@ExtendWith(MockitoExtension.class)
class BillingDashboardServiceSummaryTest {

    @Mock BillingAccountRepository billingAccountRepository;
    @Mock BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock BillingSubscriptionItemRepository itemRepository;
    @Mock InvoiceRepository invoiceRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock MaintenanceItemRepository maintenanceItemRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;

    @InjectMocks BillingDashboardService service;

    private static final Long USER_ID = 42L;

    private BillingSubscription subscription;
    private BillingPlan accountPlan;

    @BeforeEach
    void setUp() {
        var user = User.builder().id(USER_ID).build();
        var account = BillingAccount.builder().id(1L).user(user).build();
        subscription = BillingSubscription.builder()
                .id(5L).billingAccount(account).status(SubscriptionStatus.ACTIVE).cycle(BillingCycle.MONTHLY)
                .totalCents(29900L).build();
        accountPlan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(invoiceRepository.findRecentInvoices(anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    private BillingSubscriptionItem userItem() {
        return BillingSubscriptionItem.builder()
                .id(1L).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.USER).sourceId(USER_ID.toString())
                .plan(accountPlan).valueCents(29900L).build();
    }

    private BillingSubscriptionItem orgItem(String code) {
        return BillingSubscriptionItem.builder()
                .id((long) code.hashCode()).billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION).sourceId(code)
                .plan(accountPlan).valueCents(0L).activatedAt(Instant.now()).build();
    }

    @Test
    void getBillingSummary_returnsAccountPoolUsage_withMultipleOrganizations() {
        var user = userItem();
        var org1 = orgItem("ORG-001");
        var org2 = orgItem("ORG-002");

        when(itemRepository.findAllByBillingSubscriptionIdFetchPlan(5L)).thenReturn(List.of(user, org1, org2));
        when(organizationRepository.findAllByCodeIn(List.of("ORG-001", "ORG-002")))
                .thenReturn(List.of(
                        Organization.builder().code("ORG-001").name("Empresa 1").build(),
                        Organization.builder().code("ORG-002").name("Empresa 2").build()));
        when(billingPlanFeaturesHelper.parse(accountPlan))
                .thenReturn(BillingPlanFeatures.builder().maxOrganizations(3).maxUsers(10).maxItems(500).build());
        when(maintenanceItemRepository.countByOrganizationCode("ORG-001")).thenReturn(6L);
        when(maintenanceItemRepository.countByOrganizationCode("ORG-002")).thenReturn(4L);
        when(maintenanceItemRepository.countByOrganizationCodeIn(List.of("ORG-001", "ORG-002"))).thenReturn(10L);

        var ownerUo = new UserOrganization();
        ownerUo.setUser(User.builder().id(USER_ID).build());
        var memberUo = new UserOrganization();
        memberUo.setUser(User.builder().id(77L).build());
        when(userOrganizationRepository.findAllByOrganizationCodeInWithUser(List.of("ORG-001", "ORG-002")))
                .thenReturn(List.of(ownerUo, ownerUo, memberUo));

        var summary = service.getBillingSummary(USER_ID);

        assertThat(summary.getSubscription().getMaxOrganizations()).isEqualTo(3);
        assertThat(summary.getSubscription().getOrganizationsUsed()).isEqualTo(2);
        assertThat(summary.getSubscription().getMaxUsers()).isEqualTo(10);
        assertThat(summary.getSubscription().getUsersUsed())
                .as("usuários distintos entre as organizações da conta (dono + 1 membro)")
                .isEqualTo(2);
        assertThat(summary.getSubscription().getMaxItems()).isEqualTo(500);
        assertThat(summary.getSubscription().getItemsUsedTotalAccount()).isEqualTo(10);

        var org1Dto = summary.getItems().stream().filter(i -> i.getReference().equals("ORG-001")).findFirst().orElseThrow();
        var org2Dto = summary.getItems().stream().filter(i -> i.getReference().equals("ORG-002")).findFirst().orElseThrow();
        var userDto = summary.getItems().stream().filter(i -> i.getType().equals("USER")).findFirst().orElseThrow();

        assertThat(org1Dto.getItemsUsedByOrg()).isEqualTo(6L);
        assertThat(org2Dto.getItemsUsedByOrg()).isEqualTo(4L);
        assertThat(userDto.getItemsUsedByOrg()).isNull();
    }

    @Test
    void getBillingSummary_returnsZeroedUsage_whenAccountHasNoOrganizations() {
        var user = userItem();
        when(itemRepository.findAllByBillingSubscriptionIdFetchPlan(5L)).thenReturn(List.of(user));
        when(billingPlanFeaturesHelper.parse(accountPlan))
                .thenReturn(BillingPlanFeatures.builder().maxOrganizations(3).maxUsers(10).maxItems(500).build());

        var summary = service.getBillingSummary(USER_ID);

        assertThat(summary.getSubscription().getOrganizationsUsed()).isZero();
        assertThat(summary.getSubscription().getUsersUsed()).isZero();
        assertThat(summary.getSubscription().getItemsUsedTotalAccount()).isZero();
    }
}
