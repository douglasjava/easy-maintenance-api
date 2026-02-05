package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;

import java.util.List;

public class BillingAdminDTO {

    public record BillingOverviewResponse(
            BillingCounters counters,
            List<PayerSummaryResponse> topPayers,
            List<OrganizationSubscriptionDTO.SubscriptionResponse> subscriptions
    ) {}

    public record BillingCounters(
            Long totalOrganizations,
            Long totalPayers,
            Long estimatedMonthlyRevenueCents
    ) {}
}
