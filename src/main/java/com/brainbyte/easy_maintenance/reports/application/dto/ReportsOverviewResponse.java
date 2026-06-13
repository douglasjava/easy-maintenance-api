package com.brainbyte.easy_maintenance.reports.application.dto;

import java.util.List;

public record ReportsOverviewResponse(
        GlobalKpisDTO global,
        List<OrgKpiDTO> organizations
) {

    public record GlobalKpisDTO(
            long totalItems,
            long totalOverdue,
            long totalDueSoon,
            long totalMaintenancesThisMonth
    ) {}

    public record OrgKpiDTO(
            String orgCode,
            String orgName,
            long itemsTotal,
            long overdueCount,
            long dueSoonCount,
            long maintenancesThisMonth
    ) {}
}
