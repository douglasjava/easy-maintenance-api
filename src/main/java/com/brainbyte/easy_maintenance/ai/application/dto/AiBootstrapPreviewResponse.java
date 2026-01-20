package com.brainbyte.easy_maintenance.ai.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Resposta do preview de onboarding assistido por IA")
public class AiBootstrapPreviewResponse {

    @Schema(description = "Indica se a IA foi utilizada com sucesso para gerar o preview")
    private boolean usedAi;

    @Schema(description = "Tipo de empresa processado", example = "CONDOMINIO")
    private String companyType;

    @Schema(description = "Lista de itens sugeridos para manutenção")
    private List<BootstrapItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BootstrapItem {
        private String itemType;
        private String category;
        private String criticality;
        private MaintenancePreview maintenance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenancePreview {
        private String norm;
        private String periodUnit;
        private Integer periodQty;
        private Integer toleranceDays;
        private String notes;
    }
}
