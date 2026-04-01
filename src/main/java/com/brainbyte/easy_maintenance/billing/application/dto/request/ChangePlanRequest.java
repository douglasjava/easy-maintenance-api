package com.brainbyte.easy_maintenance.billing.application.dto.request;

public record ChangePlanRequest(
    String newPlanCode,
    boolean applyImmediately
) {}
