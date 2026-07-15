package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-124: visão em calendário — GET /items/calendar (sem paginação cursor).
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceItemServiceCalendarRangeTest {

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
    private static final LocalDate FROM = LocalDate.of(2026, 7, 1);
    private static final LocalDate TO = LocalDate.of(2026, 7, 31);

    private MaintenanceItem buildItem(Long id, LocalDate nextDueAt) {
        return MaintenanceItem.builder()
                .id(id)
                .organizationCode(ORG)
                .itemType("EXTINTOR")
                .itemCategory(ItemCategory.OPERATIONAL)
                .status(ItemStatus.OK)
                .nextDueAt(nextDueAt)
                .build();
    }

    @Test
    void returnsItems_withinRange_mappedToItemResponse() {
        MaintenanceItem item1 = buildItem(1L, LocalDate.of(2026, 7, 10));
        MaintenanceItem item2 = buildItem(2L, LocalDate.of(2026, 7, 20));

        when(repository.findAll(any(Specification.class))).thenReturn(List.of(item1, item2));
        when(maintenanceRepository.findItemIdsWithMaintenances(Set.of(1L, 2L))).thenReturn(Set.of());

        List<ItemResponse> result = service.findAllForCalendar(ORG, FROM, TO, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ItemResponse::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void appliesStatusItemTypeAndCategoriaFilters_viaSpecification() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());

        service.findAllForCalendar(ORG, FROM, TO, ItemStatus.NEAR_DUE, "EXTINTOR", ItemCategory.OPERATIONAL);

        verify(repository).findAll(any(Specification.class));
        verify(maintenanceRepository, never()).findItemIdsWithMaintenances(any());
    }

    @Test
    void canUpdate_isFalse_forItemsThatHaveMaintenanceRecords() {
        MaintenanceItem item1 = buildItem(1L, LocalDate.of(2026, 7, 10));

        when(repository.findAll(any(Specification.class))).thenReturn(List.of(item1));
        when(maintenanceRepository.findItemIdsWithMaintenances(Set.of(1L))).thenReturn(Set.of(1L));

        List<ItemResponse> result = service.findAllForCalendar(ORG, FROM, TO, null, null, null);

        assertThat(result.get(0).canUpdate()).isFalse();
        assertThat(result.get(0).reason()).isEqualTo("ITEM_ALREADY_USED_IN_MAINTENANCE");
    }

    @Test
    void throwsRuleException_whenFromDateIsAfterToDate() {
        assertThatThrownBy(() -> service.findAllForCalendar(ORG, TO, FROM, null, null, null))
                .isInstanceOf(RuleException.class);

        verify(repository, never()).findAll(any(Specification.class));
    }

    @Test
    void throwsRuleException_whenFromDateOrToDateIsNull() {
        assertThatThrownBy(() -> service.findAllForCalendar(ORG, null, TO, null, null, null))
                .isInstanceOf(RuleException.class);

        assertThatThrownBy(() -> service.findAllForCalendar(ORG, FROM, null, null, null, null))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void batchQuery_isSkipped_whenNoItemsInRange() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());

        List<ItemResponse> result = service.findAllForCalendar(ORG, FROM, TO, null, null, null);

        assertThat(result).isEmpty();
        verify(maintenanceRepository, never()).findItemIdsWithMaintenances(any());
    }
}
