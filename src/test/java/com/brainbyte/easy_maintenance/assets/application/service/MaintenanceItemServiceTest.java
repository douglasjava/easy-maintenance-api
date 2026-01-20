package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceItemServiceTest {

    @Mock
    private MaintenanceItemRepository maintenanceItemRepository;

    @Mock
    private ServiceBase serviceBase;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MaintenanceItemService maintenanceItemService;

    private final String ORG_ID = "org-123";

    @Test
    void shouldCreateBatchItems() {
        // Arrange
        CreateItemRequest req1 = new CreateItemRequest(
                "Extintor",
                ItemCategory.OPERATIONAL,
                Map.of("setor", "A"),
                LocalDate.now(),
                CustomPeriodUnit.MESES,
                6,
                null
        );

        CreateItemRequest req2 = new CreateItemRequest(
                "Ar Condicionado",
                ItemCategory.OPERATIONAL,
                Map.of("setor", "B"),
                LocalDate.now(),
                CustomPeriodUnit.MESES,
                12,
                null
        );

        List<CreateItemRequest> requests = List.of(req1, req2);

        when(serviceBase.resolvePeriod(any(MaintenanceItem.class))).thenReturn(Period.ofMonths(6), Period.ofMonths(12));
        when(maintenanceItemRepository.save(any(MaintenanceItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<ItemResponse> responses = maintenanceItemService.createBatch(ORG_ID, requests);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Extintor", responses.get(0).itemType());
        assertEquals("Ar Condicionado", responses.get(1).itemType());
        verify(maintenanceItemRepository, times(2)).save(any(MaintenanceItem.class));
    }
}
