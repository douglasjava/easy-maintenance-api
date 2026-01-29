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
@Schema(description = "Resposta do processamento da aplicação do bootstrap de IA")
public class AiBootstrapApplyResponse {

    private List<CreatedItem> created;
    private List<FailedItem> failed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatedItem {
        private String localId;
        private Long itemId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedItem {
        private String localId;
        private String message;
    }
}
