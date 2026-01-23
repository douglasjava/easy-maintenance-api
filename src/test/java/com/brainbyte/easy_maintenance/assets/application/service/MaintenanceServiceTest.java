package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private MaintenanceItemService maintenanceItemService;

    @InjectMocks
    private MaintenanceService maintenanceService;

    private final String ORG_ID = "org-123";

    @BeforeEach
    void setUp() {
        TenantContext.set(ORG_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldListMaintenancesWithFilters() {
        // Arrange
        Long itemId = 1L;
        LocalDate performedAt = LocalDate.now();
        String issuedBy = "Empresa X";
        Pageable pageable = PageRequest.of(0, 10);

        MaintenanceItem item = new MaintenanceItem();
        item.setId(itemId);
        item.setOrganizationCode(ORG_ID);

        Maintenance maintenance = Maintenance.builder()
                .id(10L)
                .itemId(itemId)
                .performedAt(performedAt)
                .issuedBy(issuedBy)
                .build();

        when(maintenanceItemService.findById(itemId)).thenReturn(item);
        when(maintenanceRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(maintenance)));

        // Act
        Page<MaintenanceResponse> result = maintenanceService.listByItem(ORG_ID, itemId, performedAt, issuedBy, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        MaintenanceResponse response = result.getContent().get(0);
        assertEquals(10L, response.id());
        assertEquals(itemId, response.itemId());
        assertEquals(performedAt, response.performedAt());
        assertEquals(issuedBy, response.issuedBy());

        verify(maintenanceItemService).findById(itemId);
        verify(maintenanceRepository).findAll(any(Specification.class), eq(pageable));
    }
}
