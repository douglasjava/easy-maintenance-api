package com.brainbyte.easy_maintenance.assets.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ItemTypesRequest(
        @NotBlank String name
) {
}
