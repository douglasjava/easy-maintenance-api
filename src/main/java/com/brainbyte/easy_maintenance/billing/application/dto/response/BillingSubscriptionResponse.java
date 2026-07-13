package com.brainbyte.easy_maintenance.billing.application.dto.response;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;

import java.time.Instant;
import java.time.LocalDate;

public record BillingSubscriptionResponse(
        String id,
        SubscriptionStatus status,
        BillingCycle cycle,
        Instant trialEndsAt,
        LocalDate nextDueDate,
        Instant createdAt
) {

    public record SubscriptionItemResponse(
            Long id,
            String sourceId,
            String sourceType,
            String planCode,
            String planName,
            Long valueCents,
            String nextPlanCode,
            Instant planChangeEffectiveAt,
            SubscriptionStatus status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant activatedAt
    ) {}

    public record SubscriptionItemRequest(
            Long payerUserId,
            String planCode,
            PaymentMethodType paymentMethod
    ) {}

    /**
     * EPIC-014/TASK-113 — plano único por conta: planCode/planName/valueCents/nextPlanCode
     * refletem o plano do item USER (conta), não mais um plano próprio da organização.
     * itemsUsedByOrg/itemsUsedTotalAccount/maxItemsAccount expõem o uso do pool compartilhado
     * de itens (TASK-111) para esta organização e para a conta como um todo.
     */
    public record OrganizationSubscriptionResponse(
            Long id,
            String sourceId,
            String sourceType,
            String planCode,
            String planName,
            Long valueCents,
            String nextPlanCode,
            Instant planChangeEffectiveAt,
            SubscriptionStatus status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant activatedAt,
            long itemsUsedByOrg,
            long itemsUsedTotalAccount,
            int maxItemsAccount
    ) {}
}
