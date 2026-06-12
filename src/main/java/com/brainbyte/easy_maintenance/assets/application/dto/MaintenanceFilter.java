package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;

import java.time.LocalDate;

public record MaintenanceFilter(
        Long itemId,
        LocalDate performedAt,
        LocalDate performedAtFrom,
        LocalDate performedAtTo,
        MaintenanceType type,
        String performedBy
) {}
