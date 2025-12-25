package com.brainbyte.easy_maintenance.ai.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSummaryResponse {
    private long total;
    private long ok;
    private long nearDue;
    private long overdue;
    private String prettyText; // opcional, preenchido quando pretty=true
    private boolean usedAi;
}