package com.brainbyte.easy_maintenance.assets.application.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RegisterMaintenanceRequest(
        @NotNull LocalDate performedAt,
        String issuedBy,
        String certificateNumber,
        LocalDate certificateValidUntil,
        String receiptUrl
) {}
