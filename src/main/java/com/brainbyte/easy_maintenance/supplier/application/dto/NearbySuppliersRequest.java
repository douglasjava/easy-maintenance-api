package com.brainbyte.easy_maintenance.supplier.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NearbySuppliersRequest(
        @NotBlank String serviceKey,     // ex: "EXTINTOR", "AR_COND", "SPDA"
        @NotNull Double lat,
        @NotNull Double lng,
        Integer radiusKm,      // opcional (default 20)
        Integer limit          // opcional (default max-results)
) {}
