package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;

import java.time.Instant;

public record ItemTypesResponse(
        Long id,
        String name,
        String normalizedName,
        Status status,
        Instant createdAt
) {
}
