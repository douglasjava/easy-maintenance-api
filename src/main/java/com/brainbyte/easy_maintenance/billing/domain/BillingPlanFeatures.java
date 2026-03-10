package com.brainbyte.easy_maintenance.billing.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BillingPlanFeatures {

    private int maxItems;
    private int maxUsers;
    private boolean aiEnabled;
    private String supportLevel;
    private boolean reportsEnabled;
    private int aiMonthlyCredits;
    private int maxOrganizations;
    private int emailMonthlyLimit;

}
