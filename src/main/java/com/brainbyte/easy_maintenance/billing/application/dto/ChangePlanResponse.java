package com.brainbyte.easy_maintenance.billing.application.dto;

import java.math.BigDecimal;

public record ChangePlanResponse(
    PlanChangeType type,
    Long invoiceId,
    BigDecimal amountCharged,
    String effectiveAt
) {
    public enum PlanChangeType { UPGRADE, DOWNGRADE }
}
