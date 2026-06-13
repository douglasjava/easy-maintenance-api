package com.brainbyte.easy_maintenance.reports.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;

import java.time.LocalDate;

public record ReportsMaintenanceResponse(
        Long id,
        Long itemId,
        String itemType,
        String orgCode,
        String orgName,
        LocalDate performedAt,
        MaintenanceType type,
        String performedBy,
        Integer costCents,
        LocalDate nextDueAt
) {}
