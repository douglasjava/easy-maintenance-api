package com.brainbyte.easy_maintenance.assets.application.dto;

import java.time.LocalDate;

public record MaintenanceResponse(
        Long id,
        Long itemId,
        LocalDate performedAt,
        String certificateNumber,
        String issuedBy,
        String receiptUrl
) {}
