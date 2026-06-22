package com.brainbyte.easy_maintenance.affiliates.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CommissionAdminResponse(
        Long id,
        String affiliateName,
        String affiliateEmail,
        String affiliateWhatsapp,
        Long organizationId,
        String planName,
        BigDecimal planPrice,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        String status,
        Instant paidAt,
        Instant createdAt
) {}
