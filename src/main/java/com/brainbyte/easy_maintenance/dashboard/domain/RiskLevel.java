package com.brainbyte.easy_maintenance.dashboard.domain;

import lombok.Getter;

@Getter
public enum RiskLevel {
    LOW(1), MED(2), HIGH(3);

    private final int weight;

    RiskLevel(int weight) {
        this.weight = weight;
    }

}
