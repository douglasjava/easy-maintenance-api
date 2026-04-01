package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.AccessContextDTO;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeatureAccessService {

    public static final String USER_INACTIVE_MSG = "Sua assinatura de usuário está inativa. Você ainda pode acessar os dados existentes, mas algumas operações da conta não estão permitidas.";
    public static final String USER_FULL_ACCESS_MSG = "Acesso total à conta.";
    public static final String ORG_INACTIVE_MSG = "A assinatura desta organização está inativa. Você ainda pode visualizar os dados existentes, mas as operações de gravação não são permitidas.";
    public static final String ORG_FULL_ACCESS_MSG = "Acesso total à organização.";

    private final SubscriptionAccessService subscriptionAccessService;
    private final AuthenticationService authenticationService;
    private final OrganizationRepository organizationRepository;

    public AccessContextDTO getAccessContext() {
        User user = authenticationService.getCurrentUser();
        String currentOrgCode = TenantContext.get().orElse(null);

        AccessMode userMode = subscriptionAccessService.resolveUserAccessMode(user.getId());
        SubscriptionStatus userStatus = subscriptionAccessService.getUserSubscriptionStatus(user.getId()).orElse(null);

        AccessContextDTO.AccessDetail accountAccess = AccessContextDTO.AccessDetail.builder()
                .subscriptionStatus(userStatus != null ? userStatus.name() : "NONE")
                .accessMode(userMode)
                .message(userMode != AccessMode.FULL_ACCESS ? USER_INACTIVE_MSG : USER_FULL_ACCESS_MSG)
                .build();

        List<AccessContextDTO.AccessDetail> organizationsAccess = organizationRepository.findAllByUserId(user.getId())
                .stream()
                .map(org -> {
                    AccessMode mode = subscriptionAccessService.resolveOrganizationAccessMode(org.getCode());
                    SubscriptionStatus status = subscriptionAccessService.getOrganizationSubscriptionStatus(org.getCode()).orElse(null);

                    return AccessContextDTO.AccessDetail.builder()
                            .organizationCode(org.getCode())
                            .organizationName(org.getName())
                            .subscriptionStatus(status != null ? status.name() : "NONE")
                            .accessMode(mode)
                            .message(mode != AccessMode.FULL_ACCESS ? ORG_INACTIVE_MSG : ORG_FULL_ACCESS_MSG)
                            .build();
                })
                .toList();

        AccessMode currentOrgMode = AccessMode.READ_ONLY;
        if (currentOrgCode != null) {
            currentOrgMode = subscriptionAccessService.resolveOrganizationAccessMode(currentOrgCode);
        }

        return AccessContextDTO.builder()
                .user(AccessContextDTO.UserInfo.builder()
                        .id(user.getId())
                        .name(user.getName())
                        .build())
                .accountAccess(accountAccess)
                .organizationsAccess(organizationsAccess)
                .permissions(resolvePermissions(userMode, currentOrgMode))
                .build();
    }

    private Map<String, Boolean> resolvePermissions(AccessMode userMode, AccessMode orgMode) {
        Map<String, Boolean> permissions = new HashMap<>();

        // User Account Capabilities
        boolean userFull = userMode == AccessMode.FULL_ACCESS;
        permissions.put("canViewOrganizations", true);
        permissions.put("canCreateOrganization", userFull);
        permissions.put("canManageOwnBilling", true);

        // Organization Capabilities
        boolean orgFull = orgMode == AccessMode.FULL_ACCESS;
        permissions.put("canReadDashboard", true);
        permissions.put("canCreateItem", orgFull);
        permissions.put("canEditItem", orgFull);
        permissions.put("canDeleteItem", orgFull);
        permissions.put("canRegisterMaintenance", orgFull);
        permissions.put("canManageOrganizationUsers", orgFull);
        permissions.put("canUpdateOrganization", orgFull);
        permissions.put("canManageOrganizationBilling", true);

        return permissions;
    }
}
