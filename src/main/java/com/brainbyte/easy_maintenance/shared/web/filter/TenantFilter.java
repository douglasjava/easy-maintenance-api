package com.brainbyte.easy_maintenance.shared.web.filter;

import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Component
public class TenantFilter extends OncePerRequestFilter {

  public static final String HDR = "X-Org-Id";

  private static final Set<String> BYPASS_PREFIXES = Set.of(
          "/swagger-ui",
          "/v3/api-docs",
          "/webjars",
          "/actuator",
          "/auth/login",
          "/private/admin",
          "change-password"
  );

  // endpoints fixos (ex.: auth, org register)
  private static final Set<String> BYPASS_EXACT = Set.of(
          "POST /api/v1/organizations",
          "POST /api/v1/auth/login"
  );

  private final HandlerExceptionResolver resolver;

  public TenantFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
          throws ServletException, IOException {

    String method = req.getMethod();
    String path = getPath(req);

    try {
      if (shouldBypass(method, path) || "OPTIONS".equalsIgnoreCase(method)) {
        chain.doFilter(req, res);
        return;
      }

      String tenant = req.getHeader(HDR);
      if (tenant == null || tenant.isBlank()) {
        throw new TenantException(HttpStatus.BAD_REQUEST, "Missing X-Org-Id header");
      }

      try {
        UUID.fromString(tenant);
      } catch (IllegalArgumentException e) {
        throw new TenantException(HttpStatus.BAD_REQUEST, "X-Org-Id must be a valid UUID");
      }

      TenantContext.set(tenant);
      MDC.put("orgId", tenant);

      chain.doFilter(req, res);

    } catch (TenantException ex) {
      resolver.resolveException(req, res, null, ex);
    } finally {
      TenantContext.clear();
      MDC.remove("orgId");
    }
  }

  private boolean shouldBypass(String method, String path) {

    if (BYPASS_EXACT.contains(method.toUpperCase() + " " + path)) {
      return true;
    }

    for (String prefix : BYPASS_PREFIXES) {
      if (path.contains(prefix)) return true;
    }

    return path.contains("/swagger-ui.html");
  }

  private String getPath(HttpServletRequest req) {
    String uri = req.getRequestURI();
    String ctx = req.getContextPath();
    return (ctx != null && !ctx.isEmpty()) ? uri.substring(ctx.length()) : uri;
  }
}
