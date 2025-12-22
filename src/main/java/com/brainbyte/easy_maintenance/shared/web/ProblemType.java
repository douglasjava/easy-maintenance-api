package com.brainbyte.easy_maintenance.shared.web;

import java.net.URI;

public enum ProblemType {

  VALIDATION("validation-error", "Validation error"),
  CONFLICT("conflict", "Conflict"),
  NOT_FOUND("not-found", "Not found"),
  RULES_INVALID("rules-invalid", "Rules invalid"),
  TENANT_MISSING("tenant-missing", "Invalid request"),
  TENANT_INVALID("tenant-invalid", "Invalid request"),
  UNEXPECTED("unexpected", "Unexpected error");

  private static final String BASE = "https://easy-maintenance/api/problems/";

  private final URI type;
  private final String title;

  ProblemType(String slug, String title) {
    this.type = URI.create(BASE + slug);
    this.title = title;
  }

  public URI type() {
    return type;
  }

  public String title() {
    return title;
  }

}