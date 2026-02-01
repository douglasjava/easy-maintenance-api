package com.brainbyte.easy_maintenance.admin.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import java.util.Map;

public record AdminMetricsResponse(
    long totalOrganizations,
    long totalUsers,
    Map<Plan, Long> organizationsByPlan
) {
}
