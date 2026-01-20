package com.brainbyte.easy_maintenance.ai.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request para gerar preview de onboarding assistido por IA")
public class AiBootstrapPreviewRequest {

    @NotNull
    @Schema(description = "Tipo de empresa para o onboarding", example = "CONDOMINIUM")
    private CompanyType companyType;

    @Schema(description = "Descrição adicional ou contexto livre do usuário", example = "Prédio com 2 torres e 20 andares")
    private String description;
}
