package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceExportServiceTest {

    @Mock
    private MaintenanceRepository maintenanceRepository;

    @Mock
    private SubscriptionAccessService subscriptionAccessService;

    @Mock
    private BillingPlanFeaturesHelper featuresHelper;

    @InjectMocks
    private MaintenanceExportService service;

    private BillingSubscriptionItem subscriptionItem;
    private BillingPlan plan;

    @BeforeEach
    void setUp() {
        plan = new BillingPlan();
        subscriptionItem = BillingSubscriptionItem.builder().plan(plan).build();
    }

    // -----------------------------------------------------------------------
    // Feature gate: reportsEnabled
    // -----------------------------------------------------------------------

    @Test
    void shouldThrowWhenReportsNotEnabled() {
        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(false).build();
        when(subscriptionAccessService.getOrganizationSubscriptionItem("org-1"))
                .thenReturn(Optional.of(subscriptionItem));
        when(featuresHelper.parse(plan)).thenReturn(features);

        assertThrows(NotAuthorizedException.class,
                () -> service.exportCsv("org-1", null, null, null));

        verifyNoInteractions(maintenanceRepository);
    }

    @Test
    void shouldThrowWhenNoSubscriptionExists() {
        when(subscriptionAccessService.getOrganizationSubscriptionItem("org-1"))
                .thenReturn(Optional.empty());

        assertThrows(NotAuthorizedException.class,
                () -> service.exportCsv("org-1", null, null, null));

        verifyNoInteractions(maintenanceRepository);
    }

    // -----------------------------------------------------------------------
    // CSV generation: structure and encoding
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnOnlyHeaderWhenNoRows() {
        enableReports("org-1");
        when(maintenanceRepository.findForExport(eq("org-1"), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        byte[] result = service.exportCsv("org-1", null, null, null);

        String csv = new String(result, StandardCharsets.UTF_8);
        assertEquals("ID,Item,Data da Manutenção,Tipo,Responsável,Custo (R$),Próxima Data,Norma Aplicável\n", csv);
    }

    @Test
    void shouldGenerateCsvWithAllFields() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                1L, "Extintor", LocalDate.of(2024, 5, 20), "PREVENTIVA",
                "Técnico João", 15000, LocalDate.of(2024, 11, 20), "NBR 12693");

        when(maintenanceRepository.findForExport(eq("org-1"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(row));

        byte[] result = service.exportCsv("org-1", null, null, null);

        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertEquals("1,Extintor,2024-05-20,PREVENTIVA,Técnico João,150,00,2024-11-20,NBR 12693", lines[1]);
    }

    @Test
    void shouldEscapeFieldsWithCommas() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                2L, "Gerador, Elétrico", LocalDate.of(2024, 6, 1), "CORRETIVA",
                "Empresa ABC, Ltda", null, null, null);

        when(maintenanceRepository.findForExport(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        String csv = new String(service.exportCsv("org-1", null, null, null), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertTrue(dataLine.contains("\"Gerador, Elétrico\""));
        assertTrue(dataLine.contains("\"Empresa ABC, Ltda\""));
    }

    @Test
    void shouldHandleNullCostAndDates() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                3L, "Bomba", LocalDate.of(2024, 3, 10), "PREVENTIVA",
                "Técnico", null, null, null);

        when(maintenanceRepository.findForExport(any(), any(), any(), any()))
                .thenReturn(List.of(row));

        String csv = new String(service.exportCsv("org-1", null, null, null), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        // nulls render as empty fields
        assertTrue(dataLine.endsWith(",,"));
    }

    @Test
    void shouldPassFiltersToRepository() {
        enableReports("org-1");
        when(maintenanceRepository.findForExport(eq("org-1"), eq(42L),
                eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31))))
                .thenReturn(List.of());

        service.exportCsv("org-1", 42L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        verify(maintenanceRepository).findForExport("org-1", 42L,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void enableReports(String orgCode) {
        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(true).build();
        when(subscriptionAccessService.getOrganizationSubscriptionItem(orgCode))
                .thenReturn(Optional.of(subscriptionItem));
        when(featuresHelper.parse(plan)).thenReturn(features);
    }

    private MaintenanceExportProjection buildProjection(
            Long id, String itemType, LocalDate performedAt, String maintenanceType,
            String performedBy, Integer costCents, LocalDate nextDueAt, String normAuthority) {

        return new MaintenanceExportProjection() {
            public Long getId() { return id; }
            public String getItemType() { return itemType; }
            public LocalDate getPerformedAt() { return performedAt; }
            public String getMaintenanceType() { return maintenanceType; }
            public String getPerformedBy() { return performedBy; }
            public Integer getCostCents() { return costCents; }
            public LocalDate getNextDueAt() { return nextDueAt; }
            public String getNormAuthority() { return normAuthority; }
        };
    }
}
