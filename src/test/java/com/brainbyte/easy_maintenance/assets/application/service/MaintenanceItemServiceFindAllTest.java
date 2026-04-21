package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the batch-resolved canUpdate logic in MaintenanceItemService.findAll().
 *
 * Before this change, the frontend called GET /items/{id}/can-update once per item (N+1).
 * Now the service resolves canUpdate for the entire page in a single batch query.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceItemServiceFindAllTest {

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private MaintenanceItemRepository repository;

    @Mock
    private ServiceBase serviceBase;

    @Mock
    private NormService normService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MaintenanceItemService service;

    private static final String ORG = "ORG-001";
    private static final Pageable PAGE = PageRequest.of(0, 10);

    private MaintenanceItem buildItem(Long id) {
        return MaintenanceItem.builder()
                .id(id)
                .organizationCode(ORG)
                .itemType("EXTINTOR")
                .itemCategory(ItemCategory.OPERATIONAL)
                .status(ItemStatus.OK)
                .nextDueAt(LocalDate.now().plusMonths(6))
                .build();
    }

    @BeforeEach
    void stubNormService() {
        // normId is null for all test items — resolveNormName returns null without calling NormService
    }

    @Test
    void canUpdate_isFalse_forItemsThatHaveMaintenanceRecords() {
        MaintenanceItem item1 = buildItem(1L);
        MaintenanceItem item2 = buildItem(2L);
        Page<MaintenanceItem> pageResult = new PageImpl<>(List.of(item1, item2), PAGE, 2);

        when(repository.findAll(any(Specification.class), eq(PAGE))).thenReturn(pageResult);
        // item1 has maintenances, item2 does not
        when(maintenanceRepository.findItemIdsWithMaintenances(Set.of(1L, 2L))).thenReturn(Set.of(1L));

        Page<ItemResponse> result = service.findAll(ORG, null, null, null, PAGE);

        assertThat(result.getContent()).hasSize(2);

        ItemResponse response1 = result.getContent().stream().filter(r -> r.id().equals(1L)).findFirst().orElseThrow();
        assertThat(response1.canUpdate()).isFalse();
        assertThat(response1.reason()).isEqualTo("ITEM_ALREADY_USED_IN_MAINTENANCE");

        ItemResponse response2 = result.getContent().stream().filter(r -> r.id().equals(2L)).findFirst().orElseThrow();
        assertThat(response2.canUpdate()).isTrue();
        assertThat(response2.reason()).isNull();
    }

    @Test
    void canUpdate_isTrue_forAllItems_whenNoneHaveMaintenanceRecords() {
        MaintenanceItem item1 = buildItem(10L);
        MaintenanceItem item2 = buildItem(20L);
        Page<MaintenanceItem> pageResult = new PageImpl<>(List.of(item1, item2), PAGE, 2);

        when(repository.findAll(any(Specification.class), eq(PAGE))).thenReturn(pageResult);
        when(maintenanceRepository.findItemIdsWithMaintenances(Set.of(10L, 20L))).thenReturn(Set.of());

        Page<ItemResponse> result = service.findAll(ORG, null, null, null, PAGE);

        assertThat(result.getContent()).allSatisfy(r -> {
            assertThat(r.canUpdate()).isTrue();
            assertThat(r.reason()).isNull();
        });
    }

    @Test
    void canUpdate_isFalse_forAllItems_whenAllHaveMaintenanceRecords() {
        MaintenanceItem item1 = buildItem(5L);
        MaintenanceItem item2 = buildItem(6L);
        Page<MaintenanceItem> pageResult = new PageImpl<>(List.of(item1, item2), PAGE, 2);

        when(repository.findAll(any(Specification.class), eq(PAGE))).thenReturn(pageResult);
        when(maintenanceRepository.findItemIdsWithMaintenances(Set.of(5L, 6L))).thenReturn(Set.of(5L, 6L));

        Page<ItemResponse> result = service.findAll(ORG, null, null, null, PAGE);

        assertThat(result.getContent()).allSatisfy(r -> {
            assertThat(r.canUpdate()).isFalse();
            assertThat(r.reason()).isEqualTo("ITEM_ALREADY_USED_IN_MAINTENANCE");
        });
    }

    @Test
    void batchQuery_isSkipped_whenPageIsEmpty() {
        Page<MaintenanceItem> emptyPage = new PageImpl<>(List.of(), PAGE, 0);

        when(repository.findAll(any(Specification.class), eq(PAGE))).thenReturn(emptyPage);

        Page<ItemResponse> result = service.findAll(ORG, null, null, null, PAGE);

        assertThat(result.getContent()).isEmpty();
        // findItemIdsWithMaintenances must NOT be called for an empty page
        verify(maintenanceRepository, never()).findItemIdsWithMaintenances(any());
    }

    @Test
    void batchQuery_isCalledExactlyOnce_regardlessOfPageSize() {
        List<MaintenanceItem> items = List.of(
                buildItem(1L), buildItem(2L), buildItem(3L),
                buildItem(4L), buildItem(5L)
        );
        Page<MaintenanceItem> pageResult = new PageImpl<>(items, PAGE, 5);

        when(repository.findAll(any(Specification.class), eq(PAGE))).thenReturn(pageResult);
        when(maintenanceRepository.findItemIdsWithMaintenances(anySet())).thenReturn(Set.of());

        service.findAll(ORG, null, null, null, PAGE);

        // Exactly one batch call regardless of how many items are on the page
        verify(maintenanceRepository, times(1)).findItemIdsWithMaintenances(anySet());
    }
}
