package com.brainbyte.easy_maintenance.org_users.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class OrganizationDTO {

  public record CreateOrganizationRequest(
          @Schema(example = "ORG001")
          @NotBlank String code,
          @Schema(example = "Minha Organização")
          @NotBlank String name,
          @NotNull Plan plan,
          @Schema(example = "São Paulo")
          String city,
          @Schema(example = "12.345.678/0001-90")
          String doc
  ) {}

  public record UpdateOrganizationRequest(
          @Schema(example = "Nome Atualizado")
          @NotBlank String name,
          @NotNull Plan plan,
          @Schema(example = "Rio de Janeiro")
          String city,
          @Schema(example = "12.345.678/0001-90")
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
