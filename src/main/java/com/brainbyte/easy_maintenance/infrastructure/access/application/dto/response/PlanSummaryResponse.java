package com.brainbyte.easy_maintenance.infrastructure.access.application.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlanSummaryResponse {
    private String code;
    private String name;
}
