package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingPlanFeaturesHelper {

    private final ObjectMapper objectMapper;

    public BillingPlanFeatures parse(BillingPlan plan) {
        if (plan == null || plan.getFeaturesJson() == null || plan.getFeaturesJson().isBlank()) {
            return new BillingPlanFeatures();
        }

        try {
            return objectMapper.readValue(plan.getFeaturesJson(), BillingPlanFeatures.class);
        } catch (Exception e) {
            log.error("Erro ao fazer parse do features_json do plano: {}", plan.getCode(), e);
            return new BillingPlanFeatures();
        }
    }
}
