package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;

public record CreateItemRequest(
        @NotBlank String itemType,
        @NotNull ItemCategory itemCategory,
        Map<String, Object> location,
        LocalDate lastPerformedAt,
        CustomPeriodUnit customPeriodUnit,
        Integer customPeriodQty,
        Long normId
) {
}

