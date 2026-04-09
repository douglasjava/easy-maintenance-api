package com.brainbyte.easy_maintenance.shared.web;

import com.brainbyte.easy_maintenance.commons.exceptions.*;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessScope;
import com.brainbyte.easy_maintenance.infrastructure.access.exception.SubscriptionWriteAccessDeniedException;
import com.brainbyte.easy_maintenance.shared.ratelimit.RateLimitException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error (body): {}", ex.getMessage());

        List<FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> new FieldViolation(err.getField(), err.getDefaultMessage()))
                .toList();

        ProblemDetail pd = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, ProblemType.VALIDATION,
                "One or more fields are invalid",
                request);

        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler({ConstraintViolationException.class, ValidationException.class})
    public ProblemDetail handleConstraintViolation(
            Exception ex,
            HttpServletRequest request
    ) {
        log.warn("Validation error (constraint): {}", ex.getMessage());

        ProblemDetail pd = ProblemDetails.of(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ProblemType.VALIDATION,
                "One or more constraints were violated",
                request
        );

        if (ex instanceof ConstraintViolationException cve) {
            List<FieldViolation> violations = cve.getConstraintViolations()
                    .stream()
                    .map(v -> new FieldViolation(String.valueOf(v.getPropertyPath()), v.getMessage()))
                    .toList();
            pd.setProperty("violations", violations);
        } else {
            pd.setDetail(ex.getMessage());
        }

        return pd;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflictException(ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.CONFLICT, ProblemType.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
        log.warn("Not found: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(NoContentException.class)
    public ProblemDetail handleNoContentException(NoContentException ex, HttpServletRequest request) {
        log.warn("No content: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.NO_CONTENT, ProblemType.NO_CONTENT, ex.getMessage(), request);
    }

    @ExceptionHandler(RuleException.class)
    public ProblemDetail handleRuleException(RuleException ex, HttpServletRequest request) {
        log.warn("Rules invalid: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, ProblemType.RULES_INVALID, ex.getMessage(), request);
    }

    @ExceptionHandler(TenantException.class)
    public ProblemDetail handleTenant(TenantException ex, HttpServletRequest request) {
        log.warn("Tenant error: {}", ex.getMessage());

        ProblemType type = ex.getMessage() != null && ex.getMessage().toLowerCase().contains("missing")
                ? ProblemType.TENANT_MISSING
                : ProblemType.TENANT_INVALID;

        ProblemDetail pd = ProblemDetails.of(ex.getStatus(), type, ex.getMessage(), request);
        pd.setProperty("header", "X-Org-Id");
        return pd;
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ProblemDetail handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
        log.warn("Framework ErrorResponseException: {}", ex.getMessage());

        ProblemDetail body = ex.getBody();

        if (body.getTitle() == null) body.setTitle(ProblemType.UNEXPECTED.title());

        if (request != null && body.getInstance() == null) {
            body.setInstance(URI.create(request.getRequestURI()));
        }

        Map<String, Object> props = body.getProperties();

        if(props != null) {
            if (!props.containsKey("timestamp")) {
                body.setProperty("timestamp", OffsetDateTime.now().toString());
            }

            if (request != null && !props.containsKey("method")) {
                body.setProperty("method", request.getMethod());
            }

            if (request != null && !props.containsKey("requestId")) {
                String rid = request.getHeader("X-Request-Id");
                if (rid != null && !rid.isBlank()) {
                    body.setProperty("requestId", rid);
                }
            }
        }

        return body;
    }

    @ExceptionHandler(AccessAdminException.class)
    public ProblemDetail handleAccessAdminException(AccessAdminException ex, HttpServletRequest request) {
        log.error("Permissions invalid", ex);

        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED,
                ProblemType.WITHOUT_PERMISSIONS,
                ex.getMessage(),
                request
        );

    }

    @ExceptionHandler(NotAuthorizedException.class)
    public ProblemDetail handleNotAuthorizedException(NotAuthorizedException ex, HttpServletRequest request) {
        log.error("Usuário não autenticado", ex);

        return ProblemDetails.of(
                HttpStatus.UNAUTHORIZED,
                ProblemType.NOT_AUTHENTICATED,
                ex.getMessage(),
                request
        );

    }


    @ExceptionHandler(SubscriptionWriteAccessDeniedException.class)
    public ProblemDetail handleSubscriptionDenied(SubscriptionWriteAccessDeniedException ex, HttpServletRequest request) {
        log.warn("Subscription access denied: {}", ex.getMessage());

        String title = ex.getScope() == AccessScope.USER_ACCOUNT
                ? "A assinatura do usuário não permite esta operação."
                : "A assinatura da empresa não permite operações de gravação.";

        ProblemDetail pd = ProblemDetails.of(
                HttpStatus.FORBIDDEN,
                ProblemType.SUBSCRIPTION_DENIED,
                ex.getMessage(),
                request
        );
        pd.setTitle(title);
        return pd;
    }

    @ExceptionHandler(RateLimitException.class)
    public ProblemDetail handleRateLimit(RateLimitException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("Rate limit exceeded: {} {}", request.getMethod(), request.getRequestURI());
        response.setHeader("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        return ProblemDetails.of(HttpStatus.TOO_MANY_REQUESTS, ProblemType.RATE_LIMIT_EXCEEDED, ex.getMessage(), request);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCircuitBreaker(CallNotPermittedException ex, HttpServletRequest request) {
        log.error("Circuit breaker OPEN — service unavailable: {}", ex.getMessage());
        return ProblemDetails.of(
                HttpStatus.SERVICE_UNAVAILABLE,
                ProblemType.SERVICE_UNAVAILABLE,
                "Service temporarily unavailable. Please try again later.",
                request
        );
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error", ex);

        return ProblemDetails.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemType.UNEXPECTED,
                "Unexpected internal error",
                request
        );

    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.error("Data integrity violation: {}", ex.getMessage());

        return ProblemDetails.of(
                HttpStatus.CONFLICT,
                ProblemType.CONFLICT,
                "The operation could not be completed due to a conflict with existing data.",
                request
        );

    }

    @ExceptionHandler(S3Exception.class)
    public ProblemDetail handleS3Exception(S3Exception ex, HttpServletRequest request) {
        log.error("Failed upload or download S3: {}", ex.getMessage());

        return ProblemDetails.of(
                HttpStatus.BAD_GATEWAY,
                ProblemType.UNEXPECTED,
                ex.getMessage(),
                request
        );

    }

    public record FieldViolation(String field, String message) {
    }

}
