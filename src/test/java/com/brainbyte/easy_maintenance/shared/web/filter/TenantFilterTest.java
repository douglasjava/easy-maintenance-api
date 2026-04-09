package com.brainbyte.easy_maintenance.shared.web.filter;

import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantFilterTest {

    private static final String ORG_A = "11111111-1111-1111-1111-111111111111";
    private static final String ORG_B = "22222222-2222-2222-2222-222222222222";

    private TenantFilter filter;
    private HandlerExceptionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = mock(HandlerExceptionResolver.class);
        filter = new TenantFilter(resolver);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPassWhenAuthenticatedUserBelongsToOrg() throws Exception {
        authenticateWithOrgs(List.of(ORG_A, ORG_B));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/easy-maintenance/api/v1/items");
        req.addHeader("X-Org-Id", ORG_A);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(req, res, chain));
        assertNotNull(chain.getRequest(), "Filter chain should have been called");
    }

    @Test
    void shouldReturn403WhenAuthenticatedUserDoesNotBelongToOrg() throws Exception {
        authenticateWithOrgs(List.of(ORG_A)); // usuário só tem acesso à org A

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/easy-maintenance/api/v1/items");
        req.addHeader("X-Org-Id", ORG_B); // tenta acessar org B
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(resolver).resolveException(eq(req), eq(res), isNull(),
                argThat(ex -> ex instanceof TenantException te
                        && te.getStatus().value() == 403));
    }

    @Test
    void shouldSkipOrgCheckForUnauthenticatedRequest() throws Exception {
        // sem autenticação no SecurityContext
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/easy-maintenance/api/v1/items");
        req.addHeader("X-Org-Id", ORG_A);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(req, res, chain));
    }

    @Test
    void shouldBypassCheckForLoginEndpoint() throws Exception {
        authenticateWithOrgs(List.of(ORG_A));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        // sem X-Org-Id — endpoint de login não requer
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        assertDoesNotThrow(() -> filter.doFilter(req, res, chain));
        assertNotNull(chain.getRequest(), "Login endpoint should bypass tenant check");
    }

    @Test
    void shouldReturn400ForInvalidUuid() throws Exception {
        authenticateWithOrgs(List.of(ORG_A));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/easy-maintenance/api/v1/items");
        req.addHeader("X-Org-Id", "not-a-uuid");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(resolver).resolveException(eq(req), eq(res), isNull(),
                argThat(ex -> ex instanceof TenantException te
                        && te.getStatus().value() == 400));
    }

    private void authenticateWithOrgs(List<String> orgCodes) {
        var auth = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
        auth.setDetails(orgCodes);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
