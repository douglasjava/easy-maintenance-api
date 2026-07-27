package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Resposta com os dados da manutenção registrada")
public record MaintenanceResponse(
        @Schema(description = "ID da manutenção", example = "1")
        Long id,

        @Schema(description = "ID do item de manutenção", example = "10")
        Long itemId,

        @Schema(description = "Nome/tipo do item de manutenção", example = "EXTINTOR")
        String itemType,

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
        List<MaintenanceAttachmentSimpleResponse> attachments,

        @Schema(description = "ID do usuário que criou a manutenção")
        Long createdBy,

        @Schema(description = "ID do usuário que atualizou por último")
        Long updatedBy,

        @Schema(description = "Se a manutenção foi cancelada (TASK-137) — nunca aparece na listagem/export padrão")
        boolean cancelled,

        @Schema(description = "Motivo do cancelamento, se cancelada")
        String cancelReason,

        @Schema(description = "Data/hora do cancelamento, se cancelada")
        Instant cancelledAt,

        @Schema(description = "ID do usuário que cancelou, se cancelada")
        Long cancelledBy,

        @Schema(description = "Nome do usuário que cancelou, se cancelada e resolvível (TASK-141)")
        String cancelledByName
) {
    public MaintenanceResponse(Long id, Long itemId, LocalDate performedAt, MaintenanceType type, String performedBy, Integer costCents, LocalDate nextDueAt) {
        this(id, itemId, null, performedAt, type, performedBy, costCents, nextDueAt, List.of(), null, null, false, null, null, null, null);
    }
}
