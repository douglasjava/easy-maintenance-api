package com.brainbyte.easy_maintenance.reports.application.service;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportsService {

    private final UserOrganizationRepository userOrgRepository;
    private final OrganizationRepository organizationRepository;
    private final MaintenanceItemRepository itemRepository;
    private final MaintenanceRepository maintenanceRepository;

    @Transactional(readOnly = true)
    public ReportsOverviewResponse getOverview(Long userId) {
        List<String> orgCodes = userOrgRepository.findAllByUserId(userId).stream()
                .map(uo -> uo.getOrganizationCode())
                .toList();

        if (orgCodes.isEmpty()) {
            return new ReportsOverviewResponse(
                    new ReportsOverviewResponse.GlobalKpisDTO(0, 0, 0, 0),
                    List.of()
            );
        }

        Map<String, String> orgNames = organizationRepository.findAllByCodeIn(orgCodes).stream()
                .collect(Collectors.toMap(Organization::getCode, Organization::getName));

        LocalDate today = LocalDate.now();
        LocalDate dueSoonEnd = today.plusDays(30);
        YearMonth ym = YearMonth.now();
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<ReportsOverviewResponse.OrgKpiDTO> orgKpis = orgCodes.stream()
                .map(code -> buildOrgKpi(code, orgNames.getOrDefault(code, code), today, dueSoonEnd, monthStart, monthEnd))
                .toList();

        ReportsOverviewResponse.GlobalKpisDTO global = new ReportsOverviewResponse.GlobalKpisDTO(
                orgKpis.stream().mapToLong(ReportsOverviewResponse.OrgKpiDTO::itemsTotal).sum(),
                orgKpis.stream().mapToLong(ReportsOverviewResponse.OrgKpiDTO::overdueCount).sum(),
                orgKpis.stream().mapToLong(ReportsOverviewResponse.OrgKpiDTO::dueSoonCount).sum(),
                orgKpis.stream().mapToLong(ReportsOverviewResponse.OrgKpiDTO::maintenancesThisMonth).sum()
        );

        return new ReportsOverviewResponse(global, orgKpis);
    }

    private ReportsOverviewResponse.OrgKpiDTO buildOrgKpi(String orgCode, String orgName,
                                                           LocalDate today, LocalDate dueSoonEnd,
                                                           LocalDate monthStart, LocalDate monthEnd) {
        long itemsTotal = itemRepository.countByOrganizationCode(orgCode);
        long overdueCount = itemRepository.countByOrganizationCodeAndStatus(orgCode, ItemStatus.OVERDUE);
        long dueSoonCount = itemRepository.countDueSoon(orgCode, today, dueSoonEnd);
        long maintenancesThisMonth = maintenanceRepository.countByOrgAndPerformedBetween(orgCode, monthStart, monthEnd);

        return new ReportsOverviewResponse.OrgKpiDTO(
                orgCode, orgName, itemsTotal, overdueCount, dueSoonCount, maintenancesThisMonth
        );
    }
}
