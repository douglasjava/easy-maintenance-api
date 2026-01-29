package com.brainbyte.easy_maintenance.ai.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Requisição para aplicar os cadastros gerados pela IA")
public class AiBootstrapApplyRequest {

    @NotEmpty
    @Valid
    private List<BootstrapApplyItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BootstrapApplyItem {
        
        @NotNull
        private String localId;
        
        @NotNull
        private String itemType;
        
        @NotNull
        private String category;
        
        @NotNull
        private String criticality;
        
        @NotNull
        private MaintenanceApply maintenance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaintenanceApply {
        private String norm;
        private String periodUnit;
        private Integer periodQty;
        private Integer toleranceDays;
        private String notes;
    }
}
