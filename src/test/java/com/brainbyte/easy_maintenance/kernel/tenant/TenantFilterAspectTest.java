package com.brainbyte.easy_maintenance.kernel.tenant;

import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantFilterAspectTest {

    @Mock
    private EntityManager entityManager;
    @Mock
    private Session session;
    @Mock
    private Filter hibernateFilter;
    @Mock
    private ProceedingJoinPoint joinPoint;

    private TenantFilterAspect aspect;

    private static final String ORG_A = "aaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    @BeforeEach
    void setUp() {
        aspect = new TenantFilterAspect();
        ReflectionTestUtils.setField(aspect, "entityManager", entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldThrowTenantExceptionWhenContextIsEmpty() {
        // TenantContext not set — simulates a direct repository call without X-Org-Id
        assertThatThrownBy(() -> aspect.applyTenantFilter(joinPoint))
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("X-Org-Id");

        verifyNoInteractions(entityManager, joinPoint);
    }

    @Test
    void shouldEnableFilterProceedAndDisableWhenContextIsSet() throws Throwable {
        TenantContext.set(ORG_A);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantFilterAspect.FILTER_NAME)).thenReturn(hibernateFilter);
        when(hibernateFilter.setParameter(anyString(), any())).thenReturn(hibernateFilter);
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.applyTenantFilter(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(session).enableFilter(TenantFilterAspect.FILTER_NAME);
        verify(hibernateFilter).setParameter(TenantFilterAspect.FILTER_PARAM, ORG_A);
        verify(joinPoint).proceed();
        verify(session).disableFilter(TenantFilterAspect.FILTER_NAME);
    }

    @Test
    void shouldDisableFilterEvenWhenProceedThrowsException() throws Throwable {
        TenantContext.set(ORG_A);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantFilterAspect.FILTER_NAME)).thenReturn(hibernateFilter);
        when(hibernateFilter.setParameter(anyString(), any())).thenReturn(hibernateFilter);
        when(joinPoint.proceed()).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> aspect.applyTenantFilter(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        verify(session).disableFilter(TenantFilterAspect.FILTER_NAME);
    }

    @Test
    void shouldBypassTenantFilterInSystemContext() throws Throwable {
        // System context: background job, no X-Org-Id header, cross-tenant by design
        TenantContext.setSystemContext();
        Signature sig = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(sig);
        when(sig.getName()).thenReturn("findAllByNextDueAtIn");
        when(joinPoint.proceed()).thenReturn("batch-result");

        Object result = aspect.applyTenantFilter(joinPoint);

        assertThat(result).isEqualTo("batch-result");
        verifyNoInteractions(entityManager, session, hibernateFilter);
        verify(joinPoint).proceed();
    }

    @Test
    void shouldUseCorrectOrgCodeAsFilterParameter() throws Throwable {
        String specificOrg = "org-12345-specific";
        TenantContext.set(specificOrg);
        when(entityManager.unwrap(Session.class)).thenReturn(session);
        when(session.enableFilter(TenantFilterAspect.FILTER_NAME)).thenReturn(hibernateFilter);
        when(hibernateFilter.setParameter(anyString(), any())).thenReturn(hibernateFilter);
        when(joinPoint.proceed()).thenReturn(null);

        aspect.applyTenantFilter(joinPoint);

        verify(hibernateFilter).setParameter(TenantFilterAspect.FILTER_PARAM, specificOrg);
    }
}
