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
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionAccessAspect {

    private final SubscriptionAccessService subscriptionAccessService;
    private final AuthenticationService authenticationService;

    @Before("@annotation(com.brainbyte.easy_maintenance.infrastructure.access.infrastructure.security.RequiresFullAccess) || " +
            "@within(com.brainbyte.easy_maintenance.infrastructure.access.infrastructure.security.RequiresFullAccess)")
    public void checkSubscriptionAccess(JoinPoint joinPoint) {
        RequiresFullAccess requiresFullAccess = resolveAnnotation(joinPoint);

        if (requiresFullAccess == null) {
            log.warn("RequiresFullAccess annotation not found on method or class, but pointcut matched.");
            return;
        }

        if (requiresFullAccess.scope() == AccessScope.USER_ACCOUNT) {
            checkUserAccountAccess();
        } else {
            checkOrganizationAccess();
        }
    }

    private RequiresFullAccess resolveAnnotation(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 1. Tenta encontrar no método
        RequiresFullAccess annotation = AnnotationUtils.findAnnotation(method, RequiresFullAccess.class);

        // 2. Se não encontrar no método (pode ser método de interface), tenta no método da classe alvo (impl)
        if (annotation == null) {
            Class<?> targetClass = joinPoint.getTarget().getClass();
            try {
                Method targetMethod = targetClass.getMethod(method.getName(), method.getParameterTypes());
                annotation = AnnotationUtils.findAnnotation(targetMethod, RequiresFullAccess.class);
            } catch (NoSuchMethodException ignored) {
            }
        }

        // 3. Se ainda não encontrar, busca na classe
        if (annotation == null) {
            annotation = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), RequiresFullAccess.class);
        }

        return annotation;
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
