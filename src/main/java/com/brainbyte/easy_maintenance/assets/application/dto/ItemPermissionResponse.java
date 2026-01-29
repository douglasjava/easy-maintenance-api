package com.brainbyte.easy_maintenance.assets.application.dto;

public record ItemPermissionResponse (
        boolean canUpdate,
        String reason
) {}

