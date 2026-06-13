package com.brainbyte.easy_maintenance.reports.application.service;

import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsMaintenanceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportsServiceMaintenancesTest {

    @Mock UserOrganizationRepository userOrgRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock MaintenanceItemRepository itemRepository;
    @Mock MaintenanceRepository maintenanceRepository;

    @InjectMocks ReportsService reportsService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @Test
    void listMaintenances_noOrgs_returnsEmpty() {
        when(userOrgRepository.findAllByUserId(1L)).thenReturn(List.of());

        PageResponse<ReportsMaintenanceResponse> result =
                reportsService.listMaintenances(1L, null, null, null, null, null, pageable);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    void listMaintenances_requestedOrgNotOwnedByUser_isExcluded() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001")));

        // User requests ORG-999 which they don't own
        PageResponse<ReportsMaintenanceResponse> result =
                reportsService.listMaintenances(1L, List.of("ORG-999"), null, null, null, null, pageable);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMaintenances_returnsMaintenancesWithOrgInfo() {
        String orgCode = "ORG-001";
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, orgCode)));
        when(organizationRepository.findAllByCodeIn(anyList()))
                .thenReturn(List.of(buildOrg(orgCode, "Alpha Corp")));

        Maintenance m = buildMaintenance(10L, 100L, LocalDate.of(2026, 6, 1), MaintenanceType.PREVENTIVA);
        when(maintenanceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(m), pageable, 1));

        MaintenanceItem item = buildItem(100L, orgCode, "EXTINTOR");
        when(itemRepository.findAllById(anyCollection())).thenReturn(List.of(item));

        PageResponse<ReportsMaintenanceResponse> result =
                reportsService.listMaintenances(1L, null, null, null, null, null, pageable);

        assertThat(result.totalElements()).isEqualTo(1);
        ReportsMaintenanceResponse r = result.content().get(0);
        assertThat(r.id()).isEqualTo(10L);
        assertThat(r.orgCode()).isEqualTo(orgCode);
        assertThat(r.orgName()).isEqualTo("Alpha Corp");
        assertThat(r.itemType()).isEqualTo("EXTINTOR");
        assertThat(r.type()).isEqualTo(MaintenanceType.PREVENTIVA);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listMaintenances_filterBySpecificOrg_onlyIncludesOwnedOrgs() {
        when(userOrgRepository.findAllByUserId(1L))
                .thenReturn(List.of(buildUserOrg(1L, "ORG-001"), buildUserOrg(1L, "ORG-002")));
        when(organizationRepository.findAllByCodeIn(List.of("ORG-001")))
                .thenReturn(List.of(buildOrg("ORG-001", "Alpha")));
        when(maintenanceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<ReportsMaintenanceResponse> result =
                reportsService.listMaintenances(1L, List.of("ORG-001"), null, null, null, null, pageable);

        // Only ORG-001 was intersected; no results but no error
        assertThat(result.content()).isEmpty();
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

    private Maintenance buildMaintenance(Long id, Long itemId, LocalDate performedAt, MaintenanceType type) {
        Maintenance m = new Maintenance();
        m.setId(id);
        m.setItemId(itemId);
        m.setPerformedAt(performedAt);
        m.setType(type);
        return m;
    }

    private MaintenanceItem buildItem(Long id, String orgCode, String itemType) {
        MaintenanceItem item = new MaintenanceItem();
        item.setId(id);
        item.setOrganizationCode(orgCode);
        item.setItemType(itemType);
        return item;
    }
}
