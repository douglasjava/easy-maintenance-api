package com.brainbyte.easy_maintenance.reports.application.service;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.specification.MaintenanceSpecs;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsMaintenanceResponse;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Transactional(readOnly = true)
    public PageResponse<ReportsMaintenanceResponse> listMaintenances(Long userId,
                                                                      List<String> requestedOrgCodes,
                                                                      LocalDate performedAtFrom,
                                                                      LocalDate performedAtTo,
                                                                      MaintenanceType type,
                                                                      String itemType,
                                                                      Pageable pageable) {
        List<String> userOrgCodes = userOrgRepository.findAllByUserId(userId).stream()
                .map(uo -> uo.getOrganizationCode())
                .toList();

        // Intersect with requested filter — user can never see orgs they don't belong to
        List<String> effectiveOrgCodes = (requestedOrgCodes == null || requestedOrgCodes.isEmpty())
                ? userOrgCodes
                : userOrgCodes.stream().filter(requestedOrgCodes::contains).toList();

        if (effectiveOrgCodes.isEmpty()) {
            return new PageResponse<>(List.of(), 0, 0, 0, pageable.getPageSize());
        }

        Map<String, String> orgNames = organizationRepository.findAllByCodeIn(effectiveOrgCodes).stream()
                .collect(Collectors.toMap(Organization::getCode, Organization::getName));

        Specification<com.brainbyte.easy_maintenance.assets.domain.Maintenance> spec =
                MaintenanceSpecs.filterCrossOrg(effectiveOrgCodes, performedAtFrom, performedAtTo, type, itemType);

        Page<com.brainbyte.easy_maintenance.assets.domain.Maintenance> page =
                maintenanceRepository.findAll(spec, pageable);

        // Resolve itemId → orgCode in one query (avoids N+1)
        Set<Long> itemIds = page.getContent().stream()
                .map(m -> m.getItemId())
                .collect(Collectors.toSet());
        Map<Long, MaintenanceItem> itemMap = itemIds.isEmpty() ? Map.of() :
                itemRepository.findAllById(itemIds).stream()
                        .collect(Collectors.toMap(MaintenanceItem::getId, i -> i));

        List<ReportsMaintenanceResponse> content = page.getContent().stream()
                .map(m -> {
                    MaintenanceItem item = itemMap.get(m.getItemId());
                    String orgCode = item != null ? item.getOrganizationCode() : null;
                    String orgName = orgCode != null ? orgNames.getOrDefault(orgCode, orgCode) : null;
                    return new ReportsMaintenanceResponse(
                            m.getId(), m.getItemId(),
                            item != null ? item.getItemType() : null,
                            orgCode, orgName,
                            m.getPerformedAt(), m.getType(),
                            m.getPerformedBy(), m.getCostCents(), m.getNextDueAt()
                    );
                })
                .toList();

        return new PageResponse<>(content, page.getTotalElements(), page.getTotalPages(),
                page.getNumber(), page.getSize());
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
