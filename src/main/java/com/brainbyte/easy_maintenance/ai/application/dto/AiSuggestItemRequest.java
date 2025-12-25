package com.brainbyte.easy_maintenance.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestItemRequest {
    @NotBlank
    private String orgId;
    @NotBlank
    private String description;
    private String contextItemType; // opcional
}