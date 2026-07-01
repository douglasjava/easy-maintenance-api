package com.brainbyte.easy_maintenance.reports.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.CrossOrgMaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceExportService;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceExportServiceCrossOrgTest {

    @Mock UserOrganizationRepository userOrgRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock MaintenanceRepository maintenanceRepository;
    @Mock BillingSubscriptionItemRepository subscriptionItemRepository;
    @Mock BillingPlanFeaturesHelper featuresHelper;
    @Mock SubscriptionAccessService subscriptionAccessService;
    @Mock UserRepository userRepository;

    @InjectMocks MaintenanceExportService exportService;

    @Test
    void exportCsvCrossOrg_noAuthorizedOrgs_throwsNotAuthorized() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> exportService.exportCsvCrossOrg(1L, null, null, null, null, null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void exportCsvCrossOrg_requestedOrgNotOwnedByUser_isExcluded() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));

        // ORG-999 is not in user's list → effective is empty → early exit before repo call
        assertThatThrownBy(() -> exportService.exportCsvCrossOrg(1L, List.of("ORG-999"), null, null, null, null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    void exportCsvCrossOrg_happyPath_returnsCsvWithEmpresaAndRegistradoPorColumns() {
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

        CrossOrgMaintenanceExportProjection row = buildProjection(1L, "ORG-001", "EXTINTOR",
                LocalDate.of(2026, 6, 1), "PREVENTIVA", "José", 15000, LocalDate.of(2026, 12, 1),
                "NR-23", "REGULATORY", 7L);
        when(maintenanceRepository.findForExportCrossOrg(List.of("ORG-001"), null, null, null, null))
                .thenReturn(List.of(row));

        User user = User.builder().id(7L).name("Carlos Souza").build();
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        byte[] csv = exportService.exportCsvCrossOrg(1L, null, null, null, null, null);
        String content = new String(csv, StandardCharsets.UTF_8);

        assertThat(content).contains("Empresa");
        assertThat(content).contains("Alpha Corp");
        assertThat(content).contains("EXTINTOR");
        assertThat(content).contains("150,00");
        assertThat(content).contains("NR-23");
        assertThat(content).contains("Categoria");
        assertThat(content).contains("Regulatório");
        assertThat(content).contains("Registrado por");
        assertThat(content).contains("Carlos Souza");
    }

    @Test
    void exportCsvCrossOrg_nullCreatedBy_rendersDash() {
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

        byte[] csv = exportService.exportCsvCrossOrg(1L, null, null, null, null, null);
        String content = new String(csv, StandardCharsets.UTF_8);

        // Historical records without createdBy get "—"
        assertThat(content.split("\n")[1]).endsWith(",—");
    }

    @Test
    void exportCsvCrossOrg_planNotReportsEnabled_orgFiltered() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001"), buildUserOrg(1L, "ORG-002")));

        // ORG-001 has reports disabled, ORG-002 not returned (no item)
        BillingSubscriptionItem item = buildSubscriptionItem("ORG-001");
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of("ORG-001", "ORG-002")))
                .thenReturn(List.of(item));

        BillingPlanFeatures features = BillingPlanFeatures.builder().reportsEnabled(false).build();
        when(featuresHelper.parse(any())).thenReturn(features);

        assertThatThrownBy(() -> exportService.exportCsvCrossOrg(1L, null, null, null, null, null))
                .isInstanceOf(NotAuthorizedException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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
