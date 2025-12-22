package com.brainbyte.easy_maintenance.shared.web;

import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValidException(
          MethodArgumentNotValidException ex,
          HttpServletRequest request
  ) {
    List<FieldViolation> violations = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(err -> new FieldViolation(err.getField(), err.getDefaultMessage()))
            .toList();

    ProblemDetail pd = ProblemDetails.of(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ProblemType.VALIDATION,
            "One or more fields are invalid",
            request
    );
    pd.setProperty("violations", violations);
    return pd;
  }

  @ExceptionHandler({ ConstraintViolationException.class, ValidationException.class })
  public ProblemDetail handleConstraintViolation(
          Exception ex,
          HttpServletRequest request
  ) {
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
    return ProblemDetails.of(HttpStatus.CONFLICT, ProblemType.CONFLICT, ex.getMessage(), request);
  }

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, ProblemType.NOT_FOUND, ex.getMessage(), request);
  }

  @ExceptionHandler(RuleException.class)
  public ProblemDetail handleRuleException(RuleException ex, HttpServletRequest request) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, ProblemType.RULES_INVALID, ex.getMessage(), request);
  }

  @ExceptionHandler(TenantException.class)
  public ProblemDetail handleTenant(TenantException ex, HttpServletRequest request) {

    ProblemType t = ex.getMessage().contains("Missing")
            ? ProblemType.TENANT_MISSING
            : ProblemType.TENANT_INVALID;

    ProblemDetail pd = ProblemDetails.of(ex.getStatus(), t, ex.getMessage(), request);
    pd.setProperty("header", "X-Org-Id");
    return pd;
  }

  @ExceptionHandler(ErrorResponseException.class)
  public ProblemDetail handleErrorResponse(ErrorResponseException ex, HttpServletRequest request) {
    ProblemDetail body = ex.getBody();
    if (body.getTitle() == null) body.setTitle(ProblemType.UNEXPECTED.title());
    if (body.getInstance() == null && request != null) body.setInstance(java.net.URI.create(request.getRequestURI()));
    return body;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleGeneric(Exception ex, HttpServletRequest request) {

    return ProblemDetails.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ProblemType.UNEXPECTED,
            String.format("%s - %s", "Unexpected internal error", ex.getMessage()),
            request
    );
  }

}
