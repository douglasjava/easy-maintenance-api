package com.brainbyte.easy_maintenance.shared.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestContextFilter extends OncePerRequestFilter {

  private static final String HDR_REQUEST_ID = "X-Request-Id";
  private static final String MDC_REQUEST_ID = "requestId";
  private static final String MDC_ORG_ID = "orgId";

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
          throws ServletException, IOException {

    String requestId = Optional.ofNullable(req.getHeader(HDR_REQUEST_ID))
            .filter(s -> !s.isBlank())
            .orElse(UUID.randomUUID().toString());

    MDC.put(MDC_REQUEST_ID, requestId);
    res.setHeader(HDR_REQUEST_ID, requestId);

    try {
      chain.doFilter(req, res);
    } finally {
      MDC.remove(MDC_REQUEST_ID);
      MDC.remove(MDC_ORG_ID);
    }
  }
}
