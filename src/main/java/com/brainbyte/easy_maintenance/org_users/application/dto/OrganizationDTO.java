package com.brainbyte.easy_maintenance.org_users.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class OrganizationDTO {

  public record CreateOrganizationRequest(
          @NotBlank String code,
          @NotBlank String name,
          @NotNull Plan plan,
          String city,
          String doc
  ) {}

  public record UpdateOrganizationRequest(
          @NotBlank String name,
          @NotNull Plan plan,
          String city,
          String doc
  ) {}

  public record OrganizationResponse(
          String id,
          String code,
          String name,
          Plan plan,
          String city,
          String doc
  ) {}

}
