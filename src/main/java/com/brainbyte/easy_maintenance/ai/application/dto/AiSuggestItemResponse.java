package com.brainbyte.easy_maintenance.ai.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.CustomPeriodUnit;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestItemResponse {
    private String itemType;
    private ItemCategory itemCategory; // REGULATORY ou OPERATIONAL
    private CustomPeriodUnit customPeriodUnit; // ex: MONTHS
    private Integer customPeriodQty; // ex: 6
    private List<String> tags; // ex: ["calibracao", "extintor"]
    private boolean usedAi;
}