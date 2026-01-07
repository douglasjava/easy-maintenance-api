package com.brainbyte.easy_maintenance.dashboard.infrastructure.web.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.dashboard.domain.RiskLevel;
import lombok.Builder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Builder
public record DashboardResponse(
        Kpis kpis,
        List<AttentionItem> attentionNow,
        List<CalendarDay> calendar,
        Breakdowns breakdowns,
        List<QuickAction> quickActions,
        AiBlock ai
) {
    @Builder
    public record Kpis(
            long itemsTotal,
            long overdueCount,
            long nearDueCount,
            long dueSoonCount,
            long maintenancesThisMonth,
            Integer avgDaysToResolve,
            Double complianceScore
    ) {}

    @Builder
    public record AttentionItem(
            Long itemId,
            String itemType,
            ItemCategory itemCategory,
            ItemStatus status,
            LocalDate nextDueAt,
            int daysLate,
            RiskLevel riskLevel
    ) {}

    @Builder
    public record CalendarDay(
            LocalDate date,
            long count,
            List<Long> itemIds
    ) {}

    @Builder
    public record Breakdowns(
            Map<ItemStatus, Long> byStatus,
            Map<ItemCategory, Long> byCategory,
            List<ItemTypeCount> byItemType
    ) {}

    @Builder
    public record ItemTypeCount(String itemType, long count) {}

    @Builder
    public record QuickAction(String type, String label, Map<String, Object> filter, Integer radiusKm) {}

    @Builder
    public record AiBlock(List<String> highlights, String nextBestAction) {}
}
