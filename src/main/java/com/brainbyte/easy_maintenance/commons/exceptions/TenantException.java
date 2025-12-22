package com.brainbyte.easy_maintenance.commons.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class TenantException extends RuntimeException {

  private final HttpStatus httpStatus;

  public TenantException(HttpStatus httpStatus, String message) {
    super(message);
    this.httpStatus = httpStatus;
  }

  public HttpStatus getStatus() {
    return httpStatus;
  }

}
