package com.brainbyte.easy_maintenance.ai.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta imediata ao submeter um job de IA — use o jobId para consultar o status")
public record AiJobAcceptedResponse(
        @Schema(description = "Identificador do job para polling", example = "550e8400-e29b-41d4-a716-446655440000")
        String jobId
) {}
