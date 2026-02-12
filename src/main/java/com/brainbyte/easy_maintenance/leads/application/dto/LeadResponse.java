package com.brainbyte.easy_maintenance.leads.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Resposta contendo os dados do lead criado")
public record LeadResponse(
    @Schema(description = "ID do lead", example = "1")
    Long id,

    @Schema(description = "E-mail do lead", example = "joao@exemplo.com")
    String email,

    @Schema(description = "Nome do lead", example = "João Silva")
    String name,

    @Schema(description = "Status do lead", example = "NEW")
    String status,

    @Schema(description = "Data de criação")
    Instant createdAt
) {
}
