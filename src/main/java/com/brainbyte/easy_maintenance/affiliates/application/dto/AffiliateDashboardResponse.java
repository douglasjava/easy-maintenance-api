package com.brainbyte.easy_maintenance.affiliates.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record AffiliateDashboardResponse(
        String name,
        String code,
        String link,
        long totalLeads,
        long totalConverted,
        BigDecimal pendingAmount,
        BigDecimal paidAmount,
        List<ReferralLeadResponse> leads
) {}
