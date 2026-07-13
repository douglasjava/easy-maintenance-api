package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EPIC-014 / TASK-111 — maxItems é um pool compartilhado entre todas as organizações da mesma
 * BillingSubscription (conta), não mais um teto isolado por organização.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceItemPlanLimitTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemRepository repository;
    @Mock ServiceBase serviceBase;
    @Mock NormService normService;
    @Mock AuditService auditService;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock AuthenticationService authenticationService;

    @InjectMocks MaintenanceItemService service;

    private static final String ORG = "ORG-001";
    private static final Long SUBSCRIPTION_ID = 1L;

    // ── helpers ───────────────────────────────────────────────────────────────

    private BillingSubscriptionItem userItem(BillingSubscription subscription, int maxItems) {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxItems(maxItems).build());
        return BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.USER)
                .sourceId("1")
                .plan(plan)
                .valueCents((long) plan.getPriceCents())
                .build();
    }

    private BillingSubscriptionItem orgItem(BillingSubscription subscription, String orgCode) {
        return BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION)
                .sourceId(orgCode)
                .valueCents(0L)
                .build();
    }

    /** Conta com apenas 1 organização (ORG). */
    private BillingSubscription stubSingleOrgAccount(int maxItems) {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingSubscriptionItem user = userItem(subscription, maxItems);
        BillingSubscriptionItem org = orgItem(subscription, ORG);

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, ORG))
                .thenReturn(Optional.of(org));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, org));
        return subscription;
    }

    private void stubNoSubscription() {
        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, ORG))
                .thenReturn(Optional.empty());
    }

    private void stubHappyPath() {
        when(serviceBase.resolvePeriod(any())).thenReturn(null);
        when(repository.save(any())).thenAnswer(inv -> {
            MaintenanceItem item = inv.getArgument(0);
            item.setId(1L);
            return item;
        });
        User user = new User();
        user.setId(1L);
        when(authenticationService.getCurrentUser()).thenReturn(user);
    }

    /** OPERACIONAL request — valid for validateCreate() without normId. */
    private CreateItemRequest operacionalRequest() {
        return new CreateItemRequest("Extintor CO2", ItemCategory.OPERATIONAL, null, CustomPeriodUnit.MESES, 12, null);
    }

    // ── maxItems: erro quando no limite (organização única) ──────────────────

    @Test
    void create_throwsRuleException_whenItemCountEqualsMaxItems() {
        stubSingleOrgAccount(5);
        when(repository.countByOrganizationCodeIn(List.of(ORG))).thenReturn(5L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de itens da conta atingido")
                .hasMessageContaining("5/5");

        verify(repository, never()).save(any());
    }

    @Test
    void create_throwsRuleException_whenItemCountExceedsMaxItems() {
        stubSingleOrgAccount(5);
        when(repository.countByOrganizationCodeIn(List.of(ORG))).thenReturn(8L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de itens da conta atingido");
    }

    // ── maxItems: bloqueado sem assinatura ────────────────────────────────────

    @Test
    void create_throwsRuleException_whenOrganizationHasNoSubscription() {
        stubNoSubscription();

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("assinatura");

        verify(repository, never()).save(any());
    }

    @Test
    void create_throwsRuleException_whenSubscriptionHasNoUserItem() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingSubscriptionItem org = orgItem(subscription, ORG);

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, ORG))
                .thenReturn(Optional.of(org));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(org)); // sem item USER

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("conta não possui uma assinatura ativa");

        verify(repository, never()).save(any());
    }

    // ── maxItems: permite quando abaixo do limite ─────────────────────────────

    @Test
    void create_allowsCreation_whenItemCountBelowMaxItems() {
        stubSingleOrgAccount(5);
        when(repository.countByOrganizationCodeIn(List.of(ORG))).thenReturn(4L);
        stubHappyPath();

        assertThatCode(() -> service.create(ORG, operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository).save(any());
    }

    @Test
    void create_allowsCreation_onFirstItem() {
        stubSingleOrgAccount(30);
        when(repository.countByOrganizationCodeIn(List.of(ORG))).thenReturn(0L);
        stubHappyPath();

        assertThatCode(() -> service.create(ORG, operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository).save(any());
    }

    // ── maxItems=0 significa sem limite ──────────────────────────────────────

    @Test
    void create_allowsCreation_whenMaxItemsIsZero_treatedAsUnlimited() {
        stubSingleOrgAccount(0);
        // limite 0 bypassa a verificação — countByOrganizationCodeIn não deve ser chamado
        stubHappyPath();

        assertThatCode(() -> service.create(ORG, operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository, never()).countByOrganizationCodeIn(any());
        verify(repository).save(any());
    }

    // ── pool compartilhado entre múltiplas organizações da mesma conta ───────

    @Test
    void create_sumsItemsAcrossAllOrganizationsOfAccount_whenMultipleOrgsExist() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingSubscriptionItem user = userItem(subscription, 10);
        BillingSubscriptionItem org1 = orgItem(subscription, "ORG-001");
        BillingSubscriptionItem org2 = orgItem(subscription, "ORG-002");

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-001"))
                .thenReturn(Optional.of(org1));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, org1, org2));
        // 6 itens na ORG-001 + 4 na ORG-002 = 10, no limite da conta — bloqueado mesmo que
        // ORG-001 sozinha (6) esteja abaixo do teto de 10.
        when(repository.countByOrganizationCodeIn(anyList())).thenReturn(10L);

        assertThatThrownBy(() -> service.create("ORG-001", operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("10/10")
                .hasMessageContaining("todas as suas organizações");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).countByOrganizationCodeIn(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("ORG-001", "ORG-002");
    }

    @Test
    void create_allowsCreation_whenPooledCountAcrossOrgsBelowMaxItems() {
        BillingSubscription subscription = BillingSubscription.builder().id(SUBSCRIPTION_ID).build();
        BillingSubscriptionItem user = userItem(subscription, 10);
        BillingSubscriptionItem org1 = orgItem(subscription, "ORG-001");
        BillingSubscriptionItem org2 = orgItem(subscription, "ORG-002");

        when(billingSubscriptionItemRepository.findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, "ORG-002"))
                .thenReturn(Optional.of(org2));
        when(billingSubscriptionItemRepository.findAllByBillingSubscriptionId(SUBSCRIPTION_ID))
                .thenReturn(List.of(user, org1, org2));
        when(repository.countByOrganizationCodeIn(anyList())).thenReturn(7L); // 5 na ORG-001 + 2 na ORG-002
        stubHappyPath();

        assertThatCode(() -> service.create("ORG-002", operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository).save(any());
    }
}
