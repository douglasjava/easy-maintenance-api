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
public class AiAssistantRequest {
    // orgId virá do TenantContext
    @NotBlank
    private String question;
    private String itemType; // opcional
}
