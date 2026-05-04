package com.brainbyte.easy_maintenance.ai.application.dto;

import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Status atual de um job de IA processado em background")
public record AiJobStatusResponse(
        @Schema(description = "ID do job")
        String jobId,

        @Schema(description = "Status do processamento: PENDING, PROCESSING, DONE, FAILED")
        AiJobStatus status,

        @Schema(description = "Resultado do processamento (presente quando status=DONE)")
        Object result,

        @Schema(description = "Mensagem de erro (presente quando status=FAILED)")
        String error,

        @Schema(description = "Momento de criação do job")
        Instant createdAt,

        @Schema(description = "Momento de conclusão do processamento")
        Instant completedAt
) {}
