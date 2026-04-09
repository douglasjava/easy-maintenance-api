package com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response;

import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class AccountAccessResponse {
    private String subscriptionStatus;
    private AccessMode accessMode;
    private String message;
    private PlanSummaryResponse plan;
    private AccountPermissionsResponse permissions;
    private BillingPlanFeatures features;
    /** Preenchido apenas quando subscriptionStatus = TRIAL. Null nos demais casos. */
    private Instant trialExpiresAt;
}
