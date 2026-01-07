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

    @Test
    void attentionComparatorSortsByRiskDaysLateNextDue() {
        var today = java.time.LocalDate.of(2025, 1, 10);
        var a = new RiskHeuristics.AttentionItem(1L, "EXTINTOR", ItemCategory.REGULATORY, ItemStatus.OVERDUE,
                today.minusDays(5), 5, RiskLevel.HIGH);
        var b = new RiskHeuristics.AttentionItem(2L, "ELEVADOR", ItemCategory.OPERATIONAL, ItemStatus.OVERDUE,
                today.minusDays(2), 2, RiskLevel.MED);
        var c = new RiskHeuristics.AttentionItem(3L, "PINTURA", ItemCategory.OPERATIONAL, ItemStatus.NEAR_DUE,
                today.plusDays(3), 0, RiskLevel.LOW);
        var d = new RiskHeuristics.AttentionItem(4L, "CIVIL", ItemCategory.REGULATORY, ItemStatus.NEAR_DUE,
                today.plusDays(1), 0, RiskLevel.MED);

        java.util.List<RiskHeuristics.AttentionItem> list = new java.util.ArrayList<>(java.util.List.of(c, b, d, a));
        list.sort(RiskHeuristics.attentionComparator());

        // Expected order:
        // a (HIGH, 5d late) then d (MED, near) then b (MED, 2d late) -> but MED with daysLate should come before MED near? Comparator uses daysLate desc after risk.
        // For items with same risk MED, b has 2 daysLate vs d has 0 so b should come before d.
        // Finally c (LOW)
        assertEquals(1L, list.get(0).itemId());
        assertEquals(2L, list.get(1).itemId());
        assertEquals(4L, list.get(2).itemId());
        assertEquals(3L, list.get(3).itemId());
    }
}
