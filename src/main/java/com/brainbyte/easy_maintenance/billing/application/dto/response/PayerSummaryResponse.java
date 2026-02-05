package com.brainbyte.easy_maintenance.billing.application.dto.response;

public record PayerSummaryResponse(
        Long userId,
        String name,
        String email,
        Long orgCount,
        Long totalCents
) {}
