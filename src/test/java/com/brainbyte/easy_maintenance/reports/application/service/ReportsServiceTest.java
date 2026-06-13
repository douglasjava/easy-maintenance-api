package com.brainbyte.easy_maintenance.reports.application.service;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsOverviewResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportsServiceTest {

    @Mock UserOrganizationRepository userOrgRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock MaintenanceItemRepository itemRepository;
    @Mock MaintenanceRepository maintenanceRepository;

    @InjectMocks ReportsService reportsService;

    @Test
    void getOverview_noOrgs_returnsZeroTotals() {
        when(userOrgRepository.findAllByUserId(1L)).thenReturn(List.of());

        ReportsOverviewResponse result = reportsService.getOverview(1L);

        assertThat(result.global().totalItems()).isZero();
        assertThat(result.global().totalOverdue()).isZero();
        assertThat(result.global().totalDueSoon()).isZero();
        assertThat(result.global().totalMaintenancesThisMonth()).isZero();
        assertThat(result.organizations()).isEmpty();
    }

    @Test
    void getOverview_singleOrg_returnsCorrectKpis() {
        String orgCode = "ORG-001";
        UserOrganization uo = buildUserOrg(1L, orgCode);

        when(userOrgRepository.findAllByUserId(1L)).thenReturn(List.of(uo));
        when(organizationRepository.findAllByCodeIn(List.of(orgCode)))
                .thenReturn(List.of(buildOrg(orgCode, "Empresa Alpha")));

        stubKpis(orgCode, 10L, 2L, 3L, 5L);

        ReportsOverviewResponse result = reportsService.getOverview(1L);

        assertThat(result.organizations()).hasSize(1);
        ReportsOverviewResponse.OrgKpiDTO orgKpi = result.organizations().get(0);
        assertThat(orgKpi.orgCode()).isEqualTo(orgCode);
        assertThat(orgKpi.orgName()).isEqualTo("Empresa Alpha");
        assertThat(orgKpi.itemsTotal()).isEqualTo(10L);
        assertThat(orgKpi.overdueCount()).isEqualTo(2L);
        assertThat(orgKpi.dueSoonCount()).isEqualTo(3L);
        assertThat(orgKpi.maintenancesThisMonth()).isEqualTo(5L);

        assertThat(result.global().totalItems()).isEqualTo(10L);
        assertThat(result.global().totalOverdue()).isEqualTo(2L);
        assertThat(result.global().totalDueSoon()).isEqualTo(3L);
        assertThat(result.global().totalMaintenancesThisMonth()).isEqualTo(5L);
    }

    @Test
    void getOverview_multipleOrgs_aggregatesGlobalCorrectly() {
        String code1 = "ORG-001";
        String code2 = "ORG-002";

        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, code1), buildUserOrg(1L, code2)));
        when(organizationRepository.findAllByCodeIn(anyList()))
                .thenReturn(List.of(buildOrg(code1, "Alpha"), buildOrg(code2, "Beta")));

        stubKpis(code1, 10L, 2L, 1L, 4L);
        stubKpis(code2, 20L, 5L, 3L, 7L);

        ReportsOverviewResponse result = reportsService.getOverview(1L);

        assertThat(result.organizations()).hasSize(2);
        assertThat(result.global().totalItems()).isEqualTo(30L);
        assertThat(result.global().totalOverdue()).isEqualTo(7L);
        assertThat(result.global().totalDueSoon()).isEqualTo(4L);
        assertThat(result.global().totalMaintenancesThisMonth()).isEqualTo(11L);
    }

    @Test
    void getOverview_orgNotFoundInOrgTable_usesCodeAsName() {
        String orgCode = "ORG-UNKNOWN";
        when(userOrgRepository.findAllByUserId(1L)).thenReturn(List.of(buildUserOrg(1L, orgCode)));
        when(organizationRepository.findAllByCodeIn(anyList())).thenReturn(List.of());
        stubKpis(orgCode, 5L, 0L, 0L, 0L);

        ReportsOverviewResponse result = reportsService.getOverview(1L);

        assertThat(result.organizations().get(0).orgName()).isEqualTo(orgCode);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubKpis(String orgCode, long total, long overdue, long dueSoon, long maintMonth) {
        when(itemRepository.countByOrganizationCode(orgCode)).thenReturn(total);
        when(itemRepository.countByOrganizationCodeAndStatus(orgCode, ItemStatus.OVERDUE)).thenReturn(overdue);
        when(itemRepository.countDueSoon(eq(orgCode), any(LocalDate.class), any(LocalDate.class))).thenReturn(dueSoon);
        YearMonth ym = YearMonth.now();
        when(maintenanceRepository.countByOrgAndPerformedBetween(eq(orgCode), eq(ym.atDay(1)), eq(ym.atEndOfMonth()))).thenReturn(maintMonth);
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
}
