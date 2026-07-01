package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
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
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceItemAuditTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemRepository repository;
    @Mock ServiceBase serviceBase;
    @Mock NormService normService;
    @Mock AuditService auditService;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    @Mock AuthenticationService authenticationService;

    @InjectMocks MaintenanceItemService service;

    private static final String ORG = "ORG-AUDIT";
    private static final Long USER_ID = 42L;

    private void stubAuth() {
        User user = new User();
        user.setId(USER_ID);
        when(authenticationService.getCurrentUser()).thenReturn(user);
    }

    private void stubSubscriptionUnlimited() {
        BillingPlan plan = BillingPlan.builder().code("PRO").name("Pro").priceCents(9900).build();
        when(billingPlanFeaturesHelper.parse(plan))
                .thenReturn(BillingPlanFeatures.builder().maxItems(0).build());
        BillingSubscriptionItem sub = BillingSubscriptionItem.builder().plan(plan).build();
        when(billingSubscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), anyList()))
                .thenReturn(List.of(sub));
    }

    private CreateItemRequest operacionalRequest() {
        return new CreateItemRequest("Extintor", ItemCategory.OPERATIONAL, null, CustomPeriodUnit.MESES, 12, null);
    }

    // ── create: createdBy e updatedBy preenchidos com userId correto ──────────

    @Test
    void create_setsCreatedByAndUpdatedBy_withCurrentUserId() {
        stubAuth();
        stubSubscriptionUnlimited();
        when(serviceBase.resolvePeriod(any())).thenReturn(null);
        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> {
            MaintenanceItem item = inv.getArgument(0);
            item.setId(1L);
            return item;
        });

        service.create(ORG, operacionalRequest());

        MaintenanceItem saved = captor.getValue();
        assertThat(saved.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(saved.getUpdatedBy()).isEqualTo(USER_ID);
    }

    // ── update: updatedBy atualizado, createdBy preservado ───────────────────

    @Test
    void update_setsUpdatedBy_withCurrentUserId_andPreservesCreatedBy() {
        stubAuth();
        Long differentCreatorId = 99L;
        MaintenanceItem existing = MaintenanceItem.builder()
                .id(10L)
                .organizationCode(ORG)
                .itemType("Extintor")
                .itemCategory(ItemCategory.OPERATIONAL)
                .customPeriodUnit(CustomPeriodUnit.MESES)
                .customPeriodQty(12)
                .createdBy(differentCreatorId)
                .updatedBy(differentCreatorId)
                .build();

        when(repository.findById(10L)).thenReturn(Optional.of(existing));
        when(maintenanceRepository.existsByItemId(10L)).thenReturn(false);
        when(serviceBase.resolvePeriod(any())).thenReturn(null);
        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.update(ORG, 10L, operacionalRequest());

        MaintenanceItem saved = captor.getValue();
        assertThat(saved.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(saved.getCreatedBy()).isEqualTo(differentCreatorId); // não sobrescrito
    }

    // ── create: createdBy nunca é sobrescrito em update ──────────────────────

    @Test
    void update_doesNotOverrideCreatedBy() {
        stubAuth();
        Long originalCreatorId = 7L;
        MaintenanceItem existing = MaintenanceItem.builder()
                .id(20L)
                .organizationCode(ORG)
                .itemType("Bomba")
                .itemCategory(ItemCategory.OPERATIONAL)
                .customPeriodUnit(CustomPeriodUnit.DIAS)
                .customPeriodQty(30)
                .createdBy(originalCreatorId)
                .build();

        when(repository.findById(20L)).thenReturn(Optional.of(existing));
        when(maintenanceRepository.existsByItemId(20L)).thenReturn(false);
        when(serviceBase.resolvePeriod(any())).thenReturn(null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(ORG, 20L, operacionalRequest());

        assertThat(existing.getCreatedBy()).isEqualTo(originalCreatorId);
    }

    // ── edge case: item não encontrado na validação de limit ─────────────────

    @Test
    void update_throwsNotFoundException_whenItemDoesNotExist() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(NotFoundException.class,
                () -> service.update(ORG, 999L, operacionalRequest()));
    }
}
