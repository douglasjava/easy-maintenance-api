package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;

import java.time.LocalDate;

public record ItemResponse(
        Long id,
        String organizationCode,
        String itemType,
        ItemCategory itemCategory,
        Long normId,
        CustomPeriodUnit customPeriodUnit,
        Integer customPeriodQty,
        LocalDate lastPerformedAt,
        LocalDate nextDueAt,
        ItemStatus status
) {}
