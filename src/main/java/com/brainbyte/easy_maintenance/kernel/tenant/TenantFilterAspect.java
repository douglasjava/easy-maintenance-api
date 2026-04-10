package com.brainbyte.easy_maintenance.kernel.tenant;

import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that enforces tenant isolation on MaintenanceItemRepository.
 *
 * <p>For every repository call:
 * <ul>
 *   <li>If running in system context (background job) — proceeds without tenant filter.</li>
 *   <li>If TenantContext is populated — enables the Hibernate "tenantFilter" on the current
 *       Session so that every query automatically adds {@code WHERE organization_code = :org_code}.</li>
 *   <li>If TenantContext is empty and not in system context — throws {@link TenantException}
 *       immediately (fail-fast) before any query reaches the database.</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class TenantFilterAspect {

    static final String FILTER_NAME = "tenantFilter";
    static final String FILTER_PARAM = "org_code";

    @PersistenceContext
    private EntityManager entityManager;

    @Around("execution(* com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository.*(..))")
    public Object applyTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {

        // Background/system jobs run cross-tenant by design — bypass the filter.
        if (TenantContext.isSystemContext()) {
            log.debug("TenantFilterAspect: system context detected, bypassing tenant filter for {}",
                    joinPoint.getSignature().getName());
            return joinPoint.proceed();
        }

        // Fail-fast: any user-context call without a tenant set is a programming error.
        String orgCode = TenantContext.get()
                .orElseThrow(() -> new TenantException(
                        HttpStatus.FORBIDDEN,
                        "Tenant context is required for this operation. Provide the X-Org-Id header."));

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(FILTER_NAME).setParameter(FILTER_PARAM, orgCode);
        log.debug("TenantFilterAspect: Hibernate tenant filter enabled for org={}", orgCode);

        try {
            return joinPoint.proceed();
        } finally {
            session.disableFilter(FILTER_NAME);
        }
    }
}
