package com.brainbyte.easy_maintenance.dashboard.domain;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

public final class RiskHeuristics {

    private RiskHeuristics() {}

    public static RiskLevel riskLevel(ItemCategory category, ItemStatus status) {
        if (category == null || status == null) return RiskLevel.LOW;
        boolean regulatory = category == ItemCategory.REGULATORY;
        boolean overdue = status == ItemStatus.OVERDUE;
        boolean near = status == ItemStatus.NEAR_DUE;
        if (regulatory && overdue) return RiskLevel.HIGH;
        if (regulatory && near) return RiskLevel.MED;
        if (!regulatory && overdue) return RiskLevel.MED;
        return RiskLevel.LOW;
    }

    public static int daysLate(LocalDate nextDueAt, LocalDate today, ItemStatus status) {
        if (nextDueAt == null || today == null) return 0;
        if (status == ItemStatus.OVERDUE) {
            return (int) ChronoUnit.DAYS.between(nextDueAt, today);
        }
        return 0;
    }

    public static Comparator<AttentionItem> attentionComparator() {
        return Comparator
                .comparingInt((AttentionItem it) -> it.riskLevel().getWeight()).reversed()
                .thenComparingInt(AttentionItem::daysLate).reversed()
                .thenComparing(AttentionItem::nextDueAt);
    }

    public record AttentionItem(Long itemId,
                                String itemType,
                                ItemCategory itemCategory,
                                ItemStatus status,
                                LocalDate nextDueAt,
                                int daysLate,
                                RiskLevel riskLevel) {}
}
