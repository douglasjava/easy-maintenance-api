package com.brainbyte.easy_maintenance.reports.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CrossOrgMaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceExportService;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// TASK-147 (EPIC-017): evoluiu de CSV pra .xlsx real (Apache POI) — asserções agora leem o
// workbook de volta via POI em vez de comparar string crua, já que o arquivo não é mais texto.
@ExtendWith(MockitoExtension.class)
class MaintenanceExportServiceCrossOrgTest {

    @Mock UserOrganizationRepository userOrgRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock MaintenanceRepository maintenanceRepository;
    @Mock BillingSubscriptionItemRepository subscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper featuresHelper;
    @Mock SubscriptionAccessService subscriptionAccessService;
    @Mock UserRepository userRepository;
    @Mock MaintenanceAttachmentRepository attachmentRepository;

    @InjectMocks MaintenanceExportService exportService;

    @Test
    void exportExcelCrossOrg_noAuthorizedOrgs_throwsNotAuthorized() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> exportService.exportExcelCrossOrg(1L, null, null, null, null, null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void exportExcelCrossOrg_requestedOrgNotOwnedByUser_isExcluded() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));

        // ORG-999 is not in user's list → effective is empty → early exit before repo call
        assertThatThrownBy(() -> exportService.exportExcelCrossOrg(1L, List.of("ORG-999"), null, null, null, null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void exportExcelCrossOrg_happyPath_returnsXlsxWithAllColumns() throws IOException {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));

        BillingSubscriptionItem item = buildSubscriptionItem("ORG-001");
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001")))
                .thenReturn(List.of(item));

        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(true).build();
        when(featuresHelper.parse(any())).thenReturn(features);

        when(organizationRepository.findAllByCodeIn(List.of("ORG-001")))
                .thenReturn(List.of(buildOrg("ORG-001", "Alpha Corp")));

        // nextDueAt bem no passado → status calculado deveria ser OVERDUE ("Vencido")
        CrossOrgMaintenanceExportProjection row = buildProjection(1L, "ORG-001", "EXTINTOR",
                LocalDate.of(2026, 6, 1), "PREVENTIVA", "José", 15000, LocalDate.of(2020, 1, 1),
                "NR-23", "REGULATORY", 7L);
        when(maintenanceRepository.findForExportCrossOrg(List.of("ORG-001"), null, null, null, null))
                .thenReturn(List.of(row));

        User user = User.builder().id(7L).name("Carlos Souza").build();
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        MaintenanceAttachment attachment = MaintenanceAttachment.builder()
                .id(1L).maintenanceId(1L).attachmentType(AttachmentType.PHOTO).fileUrl("s3://x").build();
        when(attachmentRepository.findByMaintenanceIdIn(Set.of(1L))).thenReturn(List.of(attachment));

        byte[] xlsx = exportService.exportExcelCrossOrg(1L, null, null, null, null, null);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(cellStrings(header)).containsExactly(
                    "ID", "Empresa", "Item", "Data da Manutenção", "Tipo", "Responsável",
                    "Custo (R$)", "Próxima Data", "Norma Aplicável", "Categoria", "Registrado por",
                    "Status do item", "Qtd. de evidências anexadas");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Alpha Corp");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("EXTINTOR");
            assertThat(dataRow.getCell(6).getNumericCellValue()).isEqualTo(150.00);
            assertThat(dataRow.getCell(8).getStringCellValue()).isEqualTo("NR-23");
            assertThat(dataRow.getCell(9).getStringCellValue()).isEqualTo("Regulatório");
            assertThat(dataRow.getCell(10).getStringCellValue()).isEqualTo("Carlos Souza");
            assertThat(dataRow.getCell(11).getStringCellValue()).isEqualTo("Vencido");
            assertThat(dataRow.getCell(12).getNumericCellValue()).isEqualTo(1.0);
        }
    }

    @Test
    void exportExcelCrossOrg_nullCreatedBy_rendersDash() throws IOException {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));

        BillingSubscriptionItem item = buildSubscriptionItem("ORG-001");
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001")))
                .thenReturn(List.of(item));

        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(true).build();
        when(featuresHelper.parse(any())).thenReturn(features);

        when(organizationRepository.findAllByCodeIn(List.of("ORG-001")))
                .thenReturn(List.of(buildOrg("ORG-001", "Alpha Corp")));

        CrossOrgMaintenanceExportProjection row = buildProjection(2L, "ORG-001", "EXTINTOR",
                LocalDate.of(2026, 6, 1), "PREVENTIVA", "José", null, null, null, null, null);
        when(maintenanceRepository.findForExportCrossOrg(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row));
        when(attachmentRepository.findByMaintenanceIdIn(Set.of(2L))).thenReturn(List.of());

        byte[] xlsx = exportService.exportExcelCrossOrg(1L, null, null, null, null, null);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            // Historical records without createdBy get "—"
            assertThat(dataRow.getCell(10).getStringCellValue()).isEqualTo("—");
            // nextDueAt null → StatusCalculator.calculate trata como OK ("Em dia")
            assertThat(dataRow.getCell(11).getStringCellValue()).isEqualTo("Em dia");
            assertThat(dataRow.getCell(12).getNumericCellValue()).isEqualTo(0.0);
        }
    }

    @Test
    void exportExcelCrossOrg_planNotReportsEnabled_orgFiltered() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001"), buildUserOrg(1L, "ORG-002")));

        // ORG-001 has reports disabled, ORG-002 not returned (no item)
        BillingSubscriptionItem item = buildSubscriptionItem("ORG-001");
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001", "ORG-002")))
                .thenReturn(List.of(item));

        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(false).build();
        when(featuresHelper.parse(any())).thenReturn(features);

        assertThatThrownBy(() -> exportService.exportExcelCrossOrg(1L, null, null, null, null, null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void exportExcelCrossOrg_multipleRows_fetchesAttachmentsInSingleBatchQuery() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));
        BillingSubscriptionItem item = buildSubscriptionItem("ORG-001");
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001")))
                .thenReturn(List.of(item));
        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(true).build();
        when(featuresHelper.parse(any())).thenReturn(features);
        when(organizationRepository.findAllByCodeIn(List.of("ORG-001")))
                .thenReturn(List.of(buildOrg("ORG-001", "Alpha Corp")));

        CrossOrgMaintenanceExportProjection row1 = buildProjection(1L, "ORG-001", "EXTINTOR",
                LocalDate.now(), "PREVENTIVA", "José", 1000, null, null, null, null);
        CrossOrgMaintenanceExportProjection row2 = buildProjection(2L, "ORG-001", "GERADOR",
                LocalDate.now(), "CORRETIVA", "Maria", 2000, null, null, null, null);
        when(maintenanceRepository.findForExportCrossOrg(any(), any(), any(), any(), any()))
                .thenReturn(List.of(row1, row2));
        when(attachmentRepository.findByMaintenanceIdIn(Set.of(1L, 2L))).thenReturn(List.of());

        exportService.exportExcelCrossOrg(1L, null, null, null, null, null);

        // Uma única chamada em lote pras 2 manutenções — nunca uma por linha (evita N+1 num export
        // de até 5000 linhas).
        verify(attachmentRepository, times(1)).findByMaintenanceIdIn(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<String> cellStrings(Row row) {
        return java.util.stream.StreamSupport.stream(row.spliterator(), false)
                .map(Cell::getStringCellValue)
                .toList();
    }

    private UserOrganization buildUserOrg(Long userId, String orgCode) {
        User user = new User();
        user.setId(userId);
        UserOrganization uo = new UserOrganization();
        uo.setUser(user);
        uo.setOrganizationCode(orgCode);
        return uo;
    }

    private Organization buildOrg(String code, String name) {
        Organization org = new Organization();
        org.setCode(code);
        org.setName(name);
        return org;
    }

    private BillingSubscriptionItem buildSubscriptionItem(String sourceId) {
        BillingSubscriptionItem item = new BillingSubscriptionItem();
        item.setSourceId(sourceId);
        item.setSourceType(BillingSubscriptionItemSourceType.ORGANIZATION);
        item.setPlan(new BillingPlan());
        return item;
    }

    private CrossOrgMaintenanceExportProjection buildProjection(
            Long id, String orgCode, String itemType, LocalDate performedAt,
            String maintenanceType, String performedBy, Integer costCents,
            LocalDate nextDueAt, String normAuthority, String itemCategory, Long createdBy) {
        return new CrossOrgMaintenanceExportProjection() {
            public Long getId() { return id; }
            public String getOrgCode() { return orgCode; }
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
