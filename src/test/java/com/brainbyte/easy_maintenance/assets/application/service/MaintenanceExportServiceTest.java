package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
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
import java.util.Set;

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

    @Mock
    private UserRepository userRepository;

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
                () -> service.exportCsv("org-1", null, null, null, null));

        verifyNoInteractions(maintenanceRepository);
    }

    @Test
    void shouldThrowWhenNoSubscriptionExists() {
        when(subscriptionAccessService.getOrganizationSubscriptionItem("org-1"))
                .thenReturn(Optional.empty());

        assertThrows(NotAuthorizedException.class,
                () -> service.exportCsv("org-1", null, null, null, null));

        verifyNoInteractions(maintenanceRepository);
    }

    // -----------------------------------------------------------------------
    // CSV generation: structure and encoding
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnOnlyHeaderWhenNoRows() {
        enableReports("org-1");
        when(maintenanceRepository.findForExport(eq("org-1"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of());

        byte[] result = service.exportCsv("org-1", null, null, null, null);

        String csv = new String(result, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿"), "CSV must start with UTF-8 BOM so Excel on Windows decodes accents correctly");
        assertEquals("﻿ID,Item,Data da Manutenção,Tipo,Responsável,Custo (R$),Próxima Data,Norma Aplicável,Categoria,Registrado por\n", csv);
    }

    @Test
    void shouldGenerateCsvWithAllFields() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                1L, "Extintor", LocalDate.of(2024, 5, 20), "PREVENTIVA",
                "Técnico João", 15000, LocalDate.of(2024, 11, 20), "NBR 12693", "REGULATORY", 42L);

        when(maintenanceRepository.findForExport(eq("org-1"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(row));

        User user = new User();
        user.setId(42L);
        user.setName("Ana Lima");
        when(userRepository.findAllById(Set.of(42L))).thenReturn(List.of(user));

        byte[] result = service.exportCsv("org-1", null, null, null, null);

        String csv = new String(result, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");
        assertEquals(2, lines.length);
        assertEquals("1,Extintor,2024-05-20,PREVENTIVA,Técnico João,\"R$ 150,00\",2024-11-20,NBR 12693,Regulatório,Ana Lima", lines[1]);
    }

    @Test
    void shouldShowDashWhenCreatedByIsNull() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                5L, "Bomba", LocalDate.of(2024, 3, 10), "PREVENTIVA",
                "Técnico", null, null, null, null, null);

        when(maintenanceRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        String csv = new String(service.exportCsv("org-1", null, null, null, null), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertTrue(dataLine.endsWith(",—"), "null createdBy must render as —");
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void shouldShowDashWhenUserNoLongerExists() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                6L, "Extintor", LocalDate.of(2024, 1, 1), "PREVENTIVA",
                "Tec", null, null, null, null, 99L);

        when(maintenanceRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(userRepository.findAllById(Set.of(99L))).thenReturn(List.of());

        String csv = new String(service.exportCsv("org-1", null, null, null, null), StandardCharsets.UTF_8);
        assertTrue(csv.split("\n")[1].endsWith(",—"), "deleted user must render as —");
    }

    @Test
    void shouldEscapeFieldsWithCommas() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                2L, "Gerador, Elétrico", LocalDate.of(2024, 6, 1), "CORRETIVA",
                "Empresa ABC, Ltda", null, null, null, "OPERATIONAL", null);

        when(maintenanceRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        String csv = new String(service.exportCsv("org-1", null, null, null, null), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        assertTrue(dataLine.contains("\"Gerador, Elétrico\""));
        assertTrue(dataLine.contains("\"Empresa ABC, Ltda\""));
    }

    @Test
    void shouldHandleNullCostAndDates() {
        enableReports("org-1");
        MaintenanceExportProjection row = buildProjection(
                3L, "Bomba", LocalDate.of(2024, 3, 10), "PREVENTIVA",
                "Técnico", null, null, null, null, null);

        when(maintenanceRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));

        String csv = new String(service.exportCsv("org-1", null, null, null, null), StandardCharsets.UTF_8);
        String dataLine = csv.split("\n")[1];

        // null cost/dates render as empty; null createdBy renders as —
        assertTrue(dataLine.contains(",,"), "null cost and nextDueAt must render as empty");
    }

    @Test
    void shouldPassFiltersToRepository() {
        enableReports("org-1");
        when(maintenanceRepository.findForExport(eq("org-1"), eq(42L),
                eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 12, 31)), isNull()))
                .thenReturn(List.of());

        service.exportCsv("org-1", 42L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null);

        verify(maintenanceRepository).findForExport("org-1", 42L,
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), null);
    }

    // -----------------------------------------------------------------------
    // Category translation
    // -----------------------------------------------------------------------

    @Test
    void shouldTranslateCategoryToPortuguese() {
        enableReports("org-1");
        MaintenanceExportProjection regulatory = buildProjection(
                10L, "Item", LocalDate.of(2024, 1, 1), "PREVENTIVA", "Tec", null, null, null, "REGULATORY", null);
        MaintenanceExportProjection operational = buildProjection(
                11L, "Item", LocalDate.of(2024, 1, 1), "PREVENTIVA", "Tec", null, null, null, "OPERATIONAL", null);

        when(maintenanceRepository.findForExport(any(), any(), any(), any(), any()))
                .thenReturn(List.of(regulatory, operational));

        String csv = new String(service.exportCsv("org-1", null, null, null, null), StandardCharsets.UTF_8);
        String[] lines = csv.split("\n");

        assertTrue(lines[1].contains(",Regulatório,"), "REGULATORY should translate to Regulatório");
        assertTrue(lines[2].contains(",Operacional,"), "OPERATIONAL should translate to Operacional");
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
            String performedBy, Integer costCents, LocalDate nextDueAt, String normAuthority,
            String itemCategory, Long createdBy) {

        return new MaintenanceExportProjection() {
            public Long getId() { return id; }
            public String getItemType() { return itemType; }
            public LocalDate getPerformedAt() { return performedAt; }
            public String getMaintenanceType() { return maintenanceType; }
            public String getPerformedBy() { return performedBy; }
            public Integer getCostCents() { return costCents; }
            public LocalDate getNextDueAt() { return nextDueAt; }
            public String getNormAuthority() { return normAuthority; }
            public String getItemCategory() { return itemCategory; }
            public Long getCreatedBy() { return createdBy; }
        };
    }
}
