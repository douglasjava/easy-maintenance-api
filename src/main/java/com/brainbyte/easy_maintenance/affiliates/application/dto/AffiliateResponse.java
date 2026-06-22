package com.brainbyte.easy_maintenance.affiliates.application.dto;

import java.math.BigDecimal;

public record AffiliateResponse(
        Long id,
        String name,
        String email,
        String code,
        String link,
        BigDecimal commissionRate
) {}
