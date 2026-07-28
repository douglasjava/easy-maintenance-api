package com.brainbyte.easy_maintenance.assets.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Requisição para cancelar uma manutenção, com motivo obrigatório")
public record CancelMaintenanceRequest(
        @Schema(description = "Motivo do cancelamento", example = "Item errado — deveria ter sido registrado no extintor da cozinha")
        @NotBlank(message = "O motivo do cancelamento é obrigatório")
        @Size(min = 5, max = 1000, message = "O motivo deve ter entre 5 e 1000 caracteres")
        String reason
) {}
