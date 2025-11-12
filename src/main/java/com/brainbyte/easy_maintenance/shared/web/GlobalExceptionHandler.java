package com.brainbyte.easy_maintenance.shared.web;

import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    pd.setTitle("Validation error");

    String errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

    pd.setDetail(errors);
    return pd;

  }

  @ExceptionHandler({ ConstraintViolationException.class, ValidationException.class })
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ProblemDetail handleConstraintViolation(Exception ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    pd.setTitle("Validation error");

    if (ex instanceof ConstraintViolationException cve) {
      String errors = cve.getConstraintViolations()
              .stream()
              .map(v -> v.getPropertyPath() + " " + v.getMessage())
              .collect(Collectors.joining(", "));

      pd.setDetail(errors);
    } else {
      pd.setDetail(ex.getMessage());
    }
    return pd;
  }

  @ExceptionHandler(ConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ProblemDetail handleConflictException(ConflictException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.CONFLICT);
    pd.setTitle("Conflict");
    pd.setDetail(ex.getMessage());
    return pd;
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ProblemDetail handleNotFoundException(NotFoundException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
    pd.setTitle("Conflict");
    pd.setDetail(ex.getMessage());
    return pd;
  }

  @ExceptionHandler(ErrorResponseException.class)
  public ProblemDetail handleErrorResponse(ErrorResponseException ex) {
    return ex.getBody();
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ProblemDetail handleGeneric(Exception ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    pd.setTitle("Unexpected error");
    pd.setDetail(ex.getMessage());
    return pd;
  }

  @ExceptionHandler(RuleException.class)
  public ProblemDetail handleRuleException(RuleException ex) {
    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle("Rules invalid");
    pd.setDetail(ex.getMessage());
    return pd;
  }

}
