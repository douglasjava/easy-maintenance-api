package com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrganizationPermissionsResponse {
    private boolean canReadDashboard;
    private boolean canCreateItem;
    private boolean canEditItem;
    private boolean canDeleteItem;
    private boolean canRegisterMaintenance;
    private boolean canManageOrganizationUsers;
    private boolean canUpdateOrganization;
    private boolean canManageOrganizationBilling;
}
