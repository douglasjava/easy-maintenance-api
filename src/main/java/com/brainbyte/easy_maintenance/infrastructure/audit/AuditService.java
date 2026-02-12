package com.brainbyte.easy_maintenance.infrastructure.audit;

import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String entityType, String entityId, AuditAction action, Object diff) {
        try {

            String diffJson = objectMapper.writeValueAsString(diff);

            var auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .diffJson(diffJson)
                    .orgCode(TenantContext.get().orElse(null))
                    .changedByUserId(getCurrentUserId())
                    .changedAt(OffsetDateTime.now())
                    .requestId(MDC.get("requestId"))
                    .build();

            getCurrentRequest().ifPresent(request -> {
                auditLog.setIp(getClientIp(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            });

            repository.save(auditLog);

        } catch (JsonProcessingException e) {
            log.error("Erro ao converter diff para JSON", e);
        } catch (Exception e) {
            log.error("Erro ao salvar log de auditoria para {} {}: {}", entityType, entityId, e.getMessage());
        }

    }

    public void logCreate(String entityType, String entityId, Object data) {
        log(entityType, entityId, AuditAction.CREATE, data);
    }

    public void logUpdate(String entityType, String entityId, Object diff) {
        log(entityType, entityId, AuditAction.UPDATE, diff);
    }

    public void logDelete(String entityType, String entityId, Object lastState) {
        log(entityType, entityId, AuditAction.DELETE, lastState);
    }

    private String getCurrentUserId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Principal::getName)
                .orElse("SYSTEM");
    }

    private Optional<HttpServletRequest> getCurrentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance)
                .map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}