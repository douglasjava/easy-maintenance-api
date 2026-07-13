package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EPIC-014 / TASK-110-113 — adicionar organização (2ª/3ª/...) não deve gerar item cobrável próprio;
 * a fonte de verdade de cobrança e de plano passa a ser apenas o item USER da BillingSubscription
 * (conta). getOrganizationSubscription() expõe o plano da conta + uso do pool de itens da org.
 */
@ExtendWith(MockitoExtension.class)
class OrganizationsServiceTest {

    @Mock OrganizationRepository repository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingSubscriptionService billingSubscriptionService;
    @Mock BillingPlanRepository billingPlanRepository;
    @Mock BillingAccountRepository billingAccountRepository;
    @Mock UserRepository userRepository;
    @Mock AffiliateService affiliateService;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock MaintenanceItemRepository maintenanceItemRepository;

    @InjectMocks OrganizationsService service;

    private static final Long PAYER_USER_ID = 42L;
    private static final Long SUBSCRIPTION_ID = 1L;

    private BillingSubscriptionItem nonBillableOrgItem(String orgCode, BillingSubscription subscription) {
        return BillingSubscriptionItem.builder()
                .id(99L)
                .billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION)
                .sourceId(orgCode)
                .valueCents(0L)
                .activatedAt(Instant.now())
                .build();
    }

    private BillingSubscriptionItem userItem(BillingSubscription subscription, BillingPlan plan) {
        return BillingSubscriptionItem.builder()
                .id(1L)
                .billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.USER)
                .sourceId(PAYER_USER_ID.toString())
                .plan(plan)
                .valueCents((long) plan.getPriceCents())
                .build();
    }

    @Test
    void addOrganizationSubscription_addsItem_withOrganizationSourceType_notUser() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).status(SubscriptionStatus.TRIAL).build();
        BillingPlan plan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build();
        BillingSubscriptionItem orgItem = nonBillableOrgItem("ORG002", subscription);
        BillingSubscriptionItem user = userItem(subscription, plan);

        when(billingSubscriptionService.findByUser(PAYER_USER_ID)).thenReturn(Optional.of(subscription));
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.USER, PAYER_USER_ID.toString()))
                .thenReturn(Optional.of(user));
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG002"))
                .thenReturn(Optional.of(orgItem));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, orgItem));
        when(billingPlanFeaturesHelper.parse(plan)).thenReturn(BillingPlanFeatures.builder().maxItems(500).build());

        var request = new BillingSubscriptionResponse.SubscriptionItemRequest(PAYER_USER_ID, "BUSINESS", PaymentMethodType.PIX);
        service.addOrganizationSubscription("ORG002", request);

        ArgumentCaptor<BillingSubscriptionItemSourceType> sourceTypeCaptor = ArgumentCaptor.forClass(BillingSubscriptionItemSourceType.class);
        verify(billingSubscriptionService).addItem(eq(subscription), sourceTypeCaptor.capture(), eq("ORG002"), eq(plan));

        assertThat(sourceTypeCaptor.getValue()).isEqualTo(BillingSubscriptionItemSourceType.ORGANIZATION);
        verify(billingSubscriptionService, never())
                .addItem(any(), eq(BillingSubscriptionItemSourceType.USER), any(), any());
    }

    // ── EPIC-014/TASK-118: organização nunca escolhe plano próprio ───────────

    @Test
    void addOrganizationSubscription_ignoresRequestedPlanCode_whenAccountAlreadyHasSubscription() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).status(SubscriptionStatus.ACTIVE).build();
        BillingPlan accountPlan = BillingPlan.builder().code("ENTERPRISE").name("Enterprise").priceCents(89900).build();
        BillingSubscriptionItem orgItem = nonBillableOrgItem("ORG-NEW", subscription);
        BillingSubscriptionItem user = userItem(subscription, accountPlan);

        when(billingSubscriptionService.findByUser(PAYER_USER_ID)).thenReturn(Optional.of(subscription));
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.USER, PAYER_USER_ID.toString()))
                .thenReturn(Optional.of(user));
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-NEW"))
                .thenReturn(Optional.of(orgItem));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, orgItem));
        when(billingPlanFeaturesHelper.parse(accountPlan)).thenReturn(BillingPlanFeatures.builder().maxItems(5000).build());

        // Request pede STARTER, mas a conta já é ENTERPRISE — o pedido deve ser ignorado.
        var request = new BillingSubscriptionResponse.SubscriptionItemRequest(PAYER_USER_ID, "STARTER", PaymentMethodType.PIX);
        service.addOrganizationSubscription("ORG-NEW", request);

        verify(billingSubscriptionService).addItem(subscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-NEW", accountPlan);
        verify(billingPlanRepository, never()).findByCode(any());
    }

    @Test
    void addOrganizationSubscription_bootstrapsUserItem_whenAccountHasNoSubscriptionYet() {
        BillingSubscription newSubscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).status(SubscriptionStatus.TRIAL).build();
        BillingPlan requestedPlan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(14900).build();
        BillingSubscriptionItem orgItem = nonBillableOrgItem("ORG-BOOT", newSubscription);

        when(billingSubscriptionService.findByUser(PAYER_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(PAYER_USER_ID)).thenReturn(Optional.of(
                com.brainbyte.easy_maintenance.org_users.domain.User.builder().id(PAYER_USER_ID).build()));
        when(billingAccountRepository.findByUserId(PAYER_USER_ID)).thenReturn(Optional.empty());
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingSubscriptionService.createTrial(any(), any())).thenReturn(newSubscription);
        when(billingPlanRepository.findByCode("STARTER")).thenReturn(Optional.of(requestedPlan));
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-BOOT"))
                .thenReturn(Optional.of(orgItem));
        // getOrganizationSubscription() (chamado ao final) precisa achar o item USER recém-criado
        // entre os "irmãos" da subscription — o mock de addItem não muta estado real.
        BillingSubscriptionItem bootstrappedUserItem = userItem(newSubscription, requestedPlan);
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(bootstrappedUserItem, orgItem));
        when(billingPlanFeaturesHelper.parse(requestedPlan)).thenReturn(BillingPlanFeatures.builder().maxItems(100).build());

        var request = new BillingSubscriptionResponse.SubscriptionItemRequest(PAYER_USER_ID, "STARTER", PaymentMethodType.PIX);
        service.addOrganizationSubscription("ORG-BOOT", request);

        verify(billingSubscriptionService).addItem(newSubscription, BillingSubscriptionItemSourceType.USER, PAYER_USER_ID.toString(), requestedPlan);
        verify(billingSubscriptionService).addItem(newSubscription, BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-BOOT", requestedPlan);
    }

    @Test
    void getOrganizationSubscription_returnsAccountPlan_notOrgItemOwnPlan() {
        BillingSubscription subscription = BillingSubscription.builder()
                .id(SUBSCRIPTION_ID).status(SubscriptionStatus.TRIAL).currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now())
                .build();
        BillingPlan accountPlan = BillingPlan.builder().code("BUSINESS").name("Business").priceCents(29900).build();
        BillingSubscriptionItem orgItem = nonBillableOrgItem("ORG001", subscription);
        BillingSubscriptionItem user = userItem(subscription, accountPlan);

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG001"))
                .thenReturn(Optional.of(orgItem));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, orgItem));
        when(billingPlanFeaturesHelper.parse(accountPlan)).thenReturn(BillingPlanFeatures.builder().maxItems(500).build());
        when(maintenanceItemRepository.countByOrganizationCode("ORG001")).thenReturn(12L);
        when(maintenanceItemRepository.countByOrganizationCodeIn(List.of("ORG001"))).thenReturn(12L);

        var response = service.getOrganizationSubscription("ORG001");

        assertThat(response.planCode()).isEqualTo("BUSINESS");
        assertThat(response.valueCents())
                .as("plano/valor refletem a conta (item USER), não mais o item ORGANIZATION (sempre 0)")
                .isEqualTo(29900L);
        assertThat(response.itemsUsedByOrg()).isEqualTo(12L);
        assertThat(response.itemsUsedTotalAccount()).isEqualTo(12L);
        assertThat(response.maxItemsAccount()).isEqualTo(500);
    }

    @Test
    void getOrganizationSubscription_sumsPoolAcrossMultipleOrganizations() {
        BillingSubscription subscription = BillingSubscription.builder()
                .id(SUBSCRIPTION_ID).status(SubscriptionStatus.ACTIVE).currentPeriodStart(Instant.now()).currentPeriodEnd(Instant.now())
                .build();
        BillingPlan accountPlan = BillingPlan.builder().code("ENTERPRISE").name("Enterprise").priceCents(89900).build();
        BillingSubscriptionItem org1 = nonBillableOrgItem("ORG-001", subscription);
        BillingSubscriptionItem org2 = nonBillableOrgItem("ORG-002", subscription);
        BillingSubscriptionItem user = userItem(subscription, accountPlan);

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-001"))
                .thenReturn(Optional.of(org1));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, org1, org2));
        when(billingPlanFeaturesHelper.parse(accountPlan)).thenReturn(BillingPlanFeatures.builder().maxItems(5000).build());
        when(maintenanceItemRepository.countByOrganizationCode("ORG-001")).thenReturn(6L);
        when(maintenanceItemRepository.countByOrganizationCodeIn(List.of("ORG-001", "ORG-002"))).thenReturn(10L);

        var response = service.getOrganizationSubscription("ORG-001");

        assertThat(response.itemsUsedByOrg()).isEqualTo(6L);
        assertThat(response.itemsUsedTotalAccount())
                .as("soma o uso de todas as organizações da conta, não só ORG-001")
                .isEqualTo(10L);
    }

    @Test
    void getOrganizationSubscription_throwsNotFound_whenOrganizationHasNoSubscriptionItem() {
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrganizationSubscription("ORG-404"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ORG-404");
    }
}
