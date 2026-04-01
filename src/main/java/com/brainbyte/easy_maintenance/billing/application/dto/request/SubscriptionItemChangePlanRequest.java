package com.brainbyte.easy_maintenance.billing.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SubscriptionItemChangePlanRequest(
    @NotBlank(message = "O código do novo plano é obrigatório")
    String newPlanCode,
    
    boolean applyImmediately
) {}
