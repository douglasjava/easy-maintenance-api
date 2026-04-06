package com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response;

import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizationAccessResponse {
    private String organizationCode;
    private String organizationName;
    private String subscriptionStatus;
    private AccessMode accessMode;
    private String message;
    private PlanSummaryResponse plan;
    private OrganizationPermissionsResponse permissions;
    private BillingPlanFeatures features;
}
