package com.brainbyte.easy_maintenance.shared.web;

import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public final class ProblemDetails {

  private static final String HDR_REQUEST_ID = "X-Request-Id";
  private static final String MDC_REQUEST_ID = "requestId";

  private ProblemDetails() {}

  public static ProblemDetail of(HttpStatus status, ProblemType problemType, String detail, HttpServletRequest request) {
    ProblemDetail pd = ProblemDetail.forStatus(status);

    pd.setType(problemType.type());
    pd.setTitle(problemType.title());
    pd.setDetail(detail);

    if (request != null) {
      pd.setInstance(URI.create(request.getRequestURI()));
      pd.setProperty("method", request.getMethod());
    }

    pd.setProperty("timestamp", OffsetDateTime.now().toString());

    String requestId = Optional.ofNullable(request)
            .map(r -> r.getHeader(HDR_REQUEST_ID))
            .filter(s -> !s.isBlank())
            .or(() -> Optional.ofNullable(MDC.get(MDC_REQUEST_ID)).filter(s -> !s.isBlank()))
            .orElseGet(() -> UUID.randomUUID().toString());

    pd.setProperty("requestId", requestId);

    try {
      TenantContext.get().ifPresent(orgId -> pd.setProperty("orgId", orgId));
    } catch (Exception ignored) {
      // caso ProblemDetails seja usado em pontos onde TenantContext não existe
    }

    return pd;
  }

}
