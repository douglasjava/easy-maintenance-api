package com.brainbyte.easy_maintenance.billing.application.dto;

public record ChangePlanRequest(
    String newPlanCode,
    boolean applyImmediately
) {}
