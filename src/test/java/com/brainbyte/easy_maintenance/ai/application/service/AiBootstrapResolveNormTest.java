package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyRequest;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiPromptTemplateRepository;
import com.brainbyte.easy_maintenance.ai.infrastructure.provider.AiProvider;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.ItemTypesRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.catalog_norms.domain.Norm;
import com.brainbyte.easy_maintenance.catalog_norms.infrastructure.persistence.NormRepository;
import com.brainbyte.easy_maintenance.infrastructure.observability.service.BusinessMetricsService;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TASK-088: Valida a lógica curated-first em resolveNorm().
 * Garante que norms do catálogo oficial têm precedência sobre AI_BOOTSTRAP.
 */
@ExtendWith(MockitoExtension.class)
class AiBootstrapResolveNormTest {

    @Mock AiPromptTemplateRepository templateRepository;
    @Mock AiProvider aiProvider;
    @Mock ItemTypesRepository itemTypesRepository;
    @Mock NormRepository normRepository;
    @Mock MaintenanceItemRepository maintenanceItemRepository;
    @Mock BusinessMetricsService businessMetricsService;

    @InjectMocks
    AiBootstrapService service;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private AiBootstrapApplyRequest.BootstrapApplyItem extintor;

    @BeforeEach
    void setUp() {
        TenantContext.set("ORG-TEST-001");

        var maintenance = AiBootstrapApplyRequest.MaintenanceApply.builder()
                .norm("ABNT NBR 12962")
                .periodUnit("MESES")
                .periodQty(12)
                .toleranceDays(30)
                .notes("Inspeção anual")
                .build();

        extintor = AiBootstrapApplyRequest.BootstrapApplyItem.builder()
                .localId("1")
                .itemType("EXTINTOR")
                .category("SEGURANCA")
                .criticality("ALTA")
                .maintenance(maintenance)
                .build();
    }

    @Test
    void resolveNorm_deveLimitarNormCurada_quandoExiste() {
        Norm curated = new Norm();
        curated.setId(10L);
        curated.setItemType("EXTINTOR");
        curated.setAuthority("ABNT / Corpo de Bombeiros");
        curated.setPeriodUnit(CustomPeriodUnit.MESES);
        curated.setPeriodQty(12);

        when(normRepository.findByItemType("EXTINTOR")).thenReturn(List.of(curated));
        when(itemTypesRepository.findByNormalizedName(any())).thenReturn(java.util.Optional.of(
                com.brainbyte.easy_maintenance.assets.domain.ItemTypes.builder()
                        .id(1L).name("EXTINTOR").normalizedName("extintor").build()));
        when(maintenanceItemRepository.save(any())).thenAnswer(inv -> {
            var item = (com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem) inv.getArgument(0);
            item.setId(100L);
            return item;
        });

        var request = AiBootstrapApplyRequest.builder()
                .items(List.of(extintor))
                .build();

        service.apply(request);

        // Não deve criar nova norm — reutilizou a curada
        verify(normRepository, never()).save(any(Norm.class));

        // O item criado deve ter normId = 10 (curada)
        ArgumentCaptor<com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem> captor =
                ArgumentCaptor.forClass(com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem.class);
        verify(maintenanceItemRepository).save(captor.capture());
        assertThat(captor.getValue().getNormId()).isEqualTo(10L);
    }

    @Test
    void resolveNorm_criaComoOperacional_quandoNaoExisteNormCurada() {
        // Nova política: sem norm curada → item OPERATIONAL, sem criar AI_BOOTSTRAP
        when(normRepository.findByItemType("EXTINTOR")).thenReturn(List.of());
        when(itemTypesRepository.findByNormalizedName(any())).thenReturn(java.util.Optional.of(
                com.brainbyte.easy_maintenance.assets.domain.ItemTypes.builder()
                        .id(1L).name("EXTINTOR").normalizedName("extintor").build()));
        when(maintenanceItemRepository.save(any())).thenAnswer(inv -> {
            var item = (com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem) inv.getArgument(0);
            item.setId(101L);
            return item;
        });

        var request = AiBootstrapApplyRequest.builder()
                .items(List.of(extintor))
                .build();

        service.apply(request);

        // Nunca deve criar AI_BOOTSTRAP
        verify(normRepository, never()).save(any(Norm.class));

        // Item criado como OPERATIONAL sem norm
        ArgumentCaptor<com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem> captor =
                ArgumentCaptor.forClass(com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem.class);
        verify(maintenanceItemRepository).save(captor.capture());
        assertThat(captor.getValue().getNormId()).isNull();
        assertThat(captor.getValue().getItemCategory())
                .isEqualTo(com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory.OPERATIONAL);
    }

    @Test
    void resolveNorm_ignoreAiBootstrapExistente_criaComoOperacional() {
        // Norm existente é AI_BOOTSTRAP → deve ser ignorada, item criado como OPERATIONAL
        Norm existingAi = new Norm();
        existingAi.setId(55L);
        existingAi.setItemType("EXTINTOR");
        existingAi.setAuthority("AI_BOOTSTRAP");
        existingAi.setPeriodUnit(CustomPeriodUnit.MESES);
        existingAi.setPeriodQty(12);

        when(normRepository.findByItemType("EXTINTOR")).thenReturn(List.of(existingAi));
        when(itemTypesRepository.findByNormalizedName(any())).thenReturn(java.util.Optional.of(
                com.brainbyte.easy_maintenance.assets.domain.ItemTypes.builder()
                        .id(2L).name("EXTINTOR").normalizedName("extintor").build()));
        when(maintenanceItemRepository.save(any())).thenAnswer(inv -> {
            var item = (com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem) inv.getArgument(0);
            item.setId(102L);
            return item;
        });

        var request = AiBootstrapApplyRequest.builder()
                .items(List.of(extintor))
                .build();

        service.apply(request);

        // AI_BOOTSTRAP existente é ignorada — não salva nova norm
        verify(normRepository, never()).save(any(Norm.class));

        // Item OPERATIONAL sem norm
        ArgumentCaptor<com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem> captor =
                ArgumentCaptor.forClass(com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem.class);
        verify(maintenanceItemRepository).save(captor.capture());
        assertThat(captor.getValue().getNormId()).isNull();
        assertThat(captor.getValue().getItemCategory())
                .isEqualTo(com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory.OPERATIONAL);
    }
}
