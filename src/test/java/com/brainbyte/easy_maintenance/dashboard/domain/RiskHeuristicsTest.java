package com.brainbyte.easy_maintenance.dashboard.domain;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RiskHeuristicsTest {

    @Test
    void riskLevelHeuristicMatchesRules() {
        assertEquals(RiskLevel.HIGH, RiskHeuristics.riskLevel(ItemCategory.REGULATORY, ItemStatus.OVERDUE));
        assertEquals(RiskLevel.MED, RiskHeuristics.riskLevel(ItemCategory.REGULATORY, ItemStatus.NEAR_DUE));
        assertEquals(RiskLevel.MED, RiskHeuristics.riskLevel(ItemCategory.OPERATIONAL, ItemStatus.OVERDUE));
        assertEquals(RiskLevel.LOW, RiskHeuristics.riskLevel(ItemCategory.OPERATIONAL, ItemStatus.NEAR_DUE));
        assertEquals(RiskLevel.LOW, RiskHeuristics.riskLevel(ItemCategory.OPERATIONAL, ItemStatus.OK));
        assertEquals(RiskLevel.LOW, RiskHeuristics.riskLevel(ItemCategory.REGULATORY, ItemStatus.OK));
    }

}
