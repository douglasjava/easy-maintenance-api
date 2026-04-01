package com.brainbyte.easy_maintenance.billing.application.dto.response;

public record ChangePlanResponse(
    PlanChangeType type,
    Long invoiceId,
    Integer amountCharged,
    String effectiveAt
) {
    public enum PlanChangeType { UPGRADE, DOWNGRADE }
}
