package com.brainbyte.easy_maintenance.catalog_norms.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;

import java.time.Instant;

public class NormDTO {

  public record NormResponse(
          String id,
          String itemType,
          CustomPeriodUnit periodUnit,
          Integer periodQty,
          Integer toleranceDays,
          String authority,
          String docUrl,
          String notes,
          Instant createdAt,
          Instant updatedAt
  ) {}

}
