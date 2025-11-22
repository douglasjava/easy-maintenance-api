package com.brainbyte.easy_maintenance.shared.web;

import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

  private static final String HDR = "X-Org-Id";
  private static final Set<String> BYPASS = Set.of(
          "POST /easy-maintenance/api/v1/organizations",
          "POST /easy-maintenance/api/v1/auth/login",
          "GET /actuator"
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

      if (isBypass(method, path) || "OPTIONS".equalsIgnoreCase(method)) {
        chain.doFilter(req, res);
        return;
      }

      String tenant = req.getHeader(HDR);
      if (tenant == null || tenant.isBlank()) {
        throw new TenantException(HttpStatus.BAD_REQUEST, "Missing X-Org-Id header");
      }
      try {
        UUID.fromString(tenant);
      }
      catch (IllegalArgumentException e) {
        throw new TenantException(HttpStatus.BAD_REQUEST, "X-Org-Id must be a valid UUID");
      }

      TenantContext.set(tenant);
      chain.doFilter(req, res);

    } catch (TenantException ex) {
      resolver.resolveException(req, res, null, ex);
    } finally {
      TenantContext.clear();
    }
  }

  private boolean isBypass(String method, String path) {
    return BYPASS.contains(method.toUpperCase() + " " + path);
  }

  private String getPath(HttpServletRequest req) {
    String uri = req.getRequestURI();
    String ctx = req.getContextPath();
    return (ctx != null && !ctx.isEmpty()) ? uri.substring(ctx.length()) : uri;
  }

}
