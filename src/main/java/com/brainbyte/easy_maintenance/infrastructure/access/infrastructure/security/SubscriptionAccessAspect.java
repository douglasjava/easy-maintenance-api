package com.brainbyte.easy_maintenance.infrastructure.access.infrastructure.security;

import com.brainbyte.easy_maintenance.infrastructure.access.application.service.SubscriptionAccessService;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessScope;
import com.brainbyte.easy_maintenance.infrastructure.access.exception.SubscriptionWriteAccessDeniedException;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionAccessAspect {

    private final SubscriptionAccessService subscriptionAccessService;
    private final AuthenticationService authenticationService;

    @Before("@annotation(requiresFullAccess) || @within(requiresFullAccess)")
    public void checkSubscriptionAccess(RequiresFullAccess requiresFullAccess) {
        if (requiresFullAccess.scope() == AccessScope.USER_ACCOUNT) {
            checkUserAccountAccess();
        } else {
            checkOrganizationAccess();
        }
    }

    private void checkUserAccountAccess() {
        User user = authenticationService.getCurrentUser();
        AccessMode mode = subscriptionAccessService.resolveUserAccessMode(user.getId());

        if (mode != AccessMode.FULL_ACCESS) {
            log.warn("Access denied for user {}: Account subscription mode is {}", user.getId(), mode);
            throw new SubscriptionWriteAccessDeniedException(AccessScope.USER_ACCOUNT);
        }
    }

    private void checkOrganizationAccess() {
        String orgCode = TenantContext.get()
                .orElseThrow(() -> new IllegalStateException("Tenant context is missing for an operation that requires organization scope."));

        AccessMode mode = subscriptionAccessService.resolveOrganizationAccessMode(orgCode);

        if (mode != AccessMode.FULL_ACCESS) {
            log.warn("Access denied for organization {}: Subscription mode is {}", orgCode, mode);
            throw new SubscriptionWriteAccessDeniedException(AccessScope.ORGANIZATION);
        }
    }
}
