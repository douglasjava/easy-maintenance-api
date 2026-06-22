package com.brainbyte.easy_maintenance.affiliates.application.dto;

import java.time.Instant;

public record ReferralLeadResponse(
        String maskedEmail,
        String status,
        Instant date
) {}
