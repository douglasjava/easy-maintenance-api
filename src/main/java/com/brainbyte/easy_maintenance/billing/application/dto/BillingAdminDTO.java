package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.List;

public class BillingAdminDTO {

    public record BillingOverviewResponse(
            BillingCounters counters,
            PageResponse<PayerResponse> payers
    ) {}

    public record BillingCounters(
            Long totalOrganizations,
            Long totalPayers,
            Long estimatedMonthlyRevenueCents
    ) {}

    public record PayerResponse(
            Long userId,
            String name,
            String email,
            Long totalPrice,
            Integer orgCount,
            SubscriptionDetail userSubscription,
            List<OrganizationDetail> organizations,
            RevenueDetail revenue
    ) {}

    public record SubscriptionDetail(
            String planCode,
            String planName,
            Long priceCents,
            SubscriptionStatus status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            java.time.Instant currentPeriodEnd
    ) {}

    public record OrganizationDetail(
            Long organizationId,
            String organizationCode,
            String organizationName,
            String planCode,
            String planName,
            Long priceCents,
            SubscriptionStatus status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            java.time.Instant currentPeriodEnd
    ) {}

    public record RevenueDetail(
            Long userCents,
            Long orgsCents,
            Long totalCents
    ) {}

    public record SubscriptionResponse(
            Long itemId,
            Long subscriptionId,
            BillingSubscriptionItemSourceType sourceType,
            String planCode,
            Long payerAccountId,
            String payerName,
            Long idUser,
            SubscriptionStatus status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            Instant periodStart,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
            Instant periodEnd,
            Long totalCents
    ) {}

}
