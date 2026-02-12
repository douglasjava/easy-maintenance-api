package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Requisição para registrar uma nova manutenção")
public record RegisterMaintenanceRequest(
        @Schema(description = "Data em que a manutenção foi realizada", example = "2024-05-20")
        @NotNull LocalDate performedAt,

        @Schema(description = "Tipo de manutenção", example = "PREVENTIVA")
        @NotNull MaintenanceType type,

        @Schema(description = "Responsável pela execução", example = "Técnico João Silva")
        String performedBy,

        @Schema(description = "Custo da manutenção em centavos", example = "15000")
        Integer costCents,

        @Schema(description = "Data da próxima manutenção", example = "2024-11-20")
        LocalDate nextDueAt
) {}
