package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Resposta com os dados da manutenção registrada")
public record MaintenanceResponse(
        @Schema(description = "ID da manutenção", example = "1")
        Long id,

        @Schema(description = "ID do item de manutenção", example = "10")
        Long itemId,

        @Schema(description = "Data em que a manutenção foi realizada", example = "2024-05-20")
        LocalDate performedAt,

        @Schema(description = "Tipo de manutenção", example = "PREVENTIVA")
        MaintenanceType type,

        @Schema(description = "Responsável pela execução", example = "Técnico João Silva")
        String performedBy,

        @Schema(description = "Custo da manutenção em centavos", example = "15000")
        Integer costCents,

        @Schema(description = "Data da próxima manutenção", example = "2024-11-20")
        LocalDate nextDueAt,

        @Schema(description = "Lista de anexos da manutenção")
        List<MaintenanceAttachmentSimpleResponse> attachments
) {
    public MaintenanceResponse(Long id, Long itemId, LocalDate performedAt, MaintenanceType type, String performedBy, Integer costCents, LocalDate nextDueAt) {
        this(id, itemId, performedAt, type, performedBy, costCents, nextDueAt, List.of());
    }
}
