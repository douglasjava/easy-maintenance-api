package com.brainbyte.easy_maintenance.leads.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitação para criação de um novo lead")
public record CreateLeadRequest(
    @Schema(description = "E-mail do lead", example = "joao@exemplo.com")
    String email,

    @Schema(description = "Nome do lead", example = "João Silva")
    String name,

    @Schema(description = "Origem do lead", example = "google")
    String source,

    @Schema(description = "Meio da campanha", example = "cpc")
    String medium,

    @Schema(description = "Nome da campanha", example = "fev-2026-launch")
    String campaign,

    @Schema(description = "Página de referência", example = "https://google.com")
    String referrer,

    @Schema(description = "Caminho da landing page", example = "/promo-verao")
    String landingPath,

    @Schema(description = "Dados UTM em formato JSON", example = "{\"utm_source\": \"google\", \"utm_medium\": \"cpc\"}")
    String utmJson
) {
}
