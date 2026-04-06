package com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountPermissionsResponse {
    private boolean canViewOrganizations;
    private boolean canCreateOrganization;
    private boolean canManageOwnBilling;
}
