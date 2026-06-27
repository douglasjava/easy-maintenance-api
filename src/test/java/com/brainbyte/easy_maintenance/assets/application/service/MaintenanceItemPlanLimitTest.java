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
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceItemPlanLimitTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemRepository repository;
    @Mock ServiceBase serviceBase;
    @Mock NormService normService;
    @Mock AuditService auditService;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;

    @InjectMocks MaintenanceItemService service;

    private static final String ORG = "ORG-001";

    // ── helpers ───────────────────────────────────────────────────────────────

    private BillingSubscriptionItem subItemWithMaxItems(int maxItems) {
        BillingPlan plan = BillingPlan.builder().code("STARTER").name("Starter").priceCents(4900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxItems(maxItems).build());
        return BillingSubscriptionItem.builder().plan(plan).build();
    }

    private void stubSubscription(BillingSubscriptionItem subItem) {
        when(billingSubscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of(subItem));
    }

    private void stubNoSubscription() {
        when(billingSubscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of());
    }

    private void stubHappyPath() {
        when(serviceBase.resolvePeriod(any())).thenReturn(null);
        when(repository.save(any())).thenAnswer(inv -> {
            MaintenanceItem item = inv.getArgument(0);
            item.setId(1L);
            return item;
        });
    }

    /** OPERACIONAL request — valid for validateCreate() without normId. */
    private CreateItemRequest operacionalRequest() {
        return new CreateItemRequest("Extintor CO2", ItemCategory.OPERATIONAL, null, CustomPeriodUnit.MESES, 12, null);
    }

    // ── maxItems: erro quando no limite ──────────────────────────────────────

    @Test
    void create_throwsRuleException_whenItemCountEqualsMaxItems() {
        stubSubscription(subItemWithMaxItems(5));
        when(repository.countByOrganizationCode(ORG)).thenReturn(5L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de itens atingido")
                .hasMessageContaining("5/5");

        verify(repository, never()).save(any());
    }

    @Test
    void create_throwsRuleException_whenItemCountExceedsMaxItems() {
        stubSubscription(subItemWithMaxItems(5));
        when(repository.countByOrganizationCode(ORG)).thenReturn(8L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de itens atingido");
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

    // ── maxItems: permite quando abaixo do limite ─────────────────────────────

    @Test
    void create_allowsCreation_whenItemCountBelowMaxItems() {
        stubSubscription(subItemWithMaxItems(5));
        when(repository.countByOrganizationCode(ORG)).thenReturn(4L);
        stubHappyPath();

        assertThatCode(() -> service.create(ORG, operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository).save(any());
    }

    @Test
    void create_allowsCreation_onFirstItem() {
        stubSubscription(subItemWithMaxItems(30));
        when(repository.countByOrganizationCode(ORG)).thenReturn(0L);
        stubHappyPath();

        assertThatCode(() -> service.create(ORG, operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository).save(any());
    }

    // ── maxItems=0 significa sem limite ──────────────────────────────────────

    @Test
    void create_allowsCreation_whenMaxItemsIsZero_treatedAsUnlimited() {
        stubSubscription(subItemWithMaxItems(0));
        // countByOrganizationCode não deve ser chamado — limite 0 bypassa a verificação
        stubHappyPath();

        assertThatCode(() -> service.create(ORG, operacionalRequest()))
                .doesNotThrowAnyException();

        verify(repository, never()).countByOrganizationCode(any());
        verify(repository).save(any());
    }

    // ── limite exato por plano (parametrizado por maxItems configurado no plano) ──

    @Test
    void create_throwsRuleException_whenAtExactFreeLimit() {
        stubSubscription(subItemWithMaxItems(10)); // FREE = 10 itens
        when(repository.countByOrganizationCode(ORG)).thenReturn(10L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("10/10");
    }

    @Test
    void create_throwsRuleException_whenAtExactStarterLimit() {
        stubSubscription(subItemWithMaxItems(50)); // STARTER = 50 itens
        when(repository.countByOrganizationCode(ORG)).thenReturn(50L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("50/50");
    }

    @Test
    void create_throwsRuleException_whenAtExactBusinessLimit() {
        stubSubscription(subItemWithMaxItems(200)); // BUSINESS = 200 itens
        when(repository.countByOrganizationCode(ORG)).thenReturn(200L);

        assertThatThrownBy(() -> service.create(ORG, operacionalRequest()))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("200/200");
    }
}
