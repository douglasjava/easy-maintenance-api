package com.brainbyte.easy_maintenance.dashboard.application;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.dashboard.domain.RiskHeuristics;
import com.brainbyte.easy_maintenance.dashboard.domain.RiskLevel;
import com.brainbyte.easy_maintenance.dashboard.infrastructure.web.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MaintenanceItemRepository itemRepo;
    private final MaintenanceRepository maintenanceRepo;
    private final Optional<ChatClient> chatClient; // present if AI configured
    private final DashboardProperties props;

    private final com.github.benmanes.caffeine.cache.Cache<String, DashboardResponse.AiBlock> aiCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(Duration.parse("PT15M"))
                    .maximumSize(1000)
                    .build();

    public DashboardResponse getDashboard(String orgId, int daysAhead, int nearDueThresholdDays, int limitAttention) {
        LocalDate today = LocalDate.now();
        LocalDate endSoon = today.plusDays(daysAhead);
        LocalDate endNear = today.plusDays(nearDueThresholdDays);

        long itemsTotal = itemRepo.countByOrganizationCode(orgId);
        long overdueCount = itemRepo.countByOrganizationCodeAndStatus(orgId, ItemStatus.OVERDUE);
        long okCount = itemRepo.countByOrganizationCodeAndStatus(orgId, ItemStatus.OK);
        long nearDueCount = itemRepo.countDueBetween(orgId, today, endNear);
        long dueSoonCount = itemRepo.countDueSoon(orgId, today, endSoon);

        YearMonth ym = YearMonth.now();
        long maintenancesThisMonth = maintenanceRepo.countByOrgAndPerformedBetween(orgId, ym.atDay(1), ym.atEndOfMonth());

        Integer avgDaysToResolve = maintenanceRepo.avgDaysToResolveLast90(orgId, Instant.now().minus(Duration.ofDays(90)));
        Double complianceScore = (itemsTotal == 0) ? null : (okCount / (double) itemsTotal);

        DashboardResponse.Kpis kpis = DashboardResponse.Kpis.builder()
                .itemsTotal(itemsTotal)
                .overdueCount(overdueCount)
                .nearDueCount(nearDueCount)
                .dueSoonCount(dueSoonCount)
                .maintenancesThisMonth(maintenancesThisMonth)
                .avgDaysToResolve(avgDaysToResolve)
                .complianceScore(complianceScore)
                .build();

        // Attention now
        List<MaintenanceItem> candidates = itemRepo.findAttentionCandidates(orgId);
        List<DashboardResponse.AttentionItem> attentionList = candidates.stream()
                .map(mi -> {
                    RiskLevel risk = RiskHeuristics.riskLevel(mi.getItemCategory(), mi.getStatus());
                    int daysLate = RiskHeuristics.daysLate(mi.getNextDueAt(), today, mi.getStatus());
                    return DashboardResponse.AttentionItem.builder()
                            .itemId(mi.getId())
                            .itemType(mi.getItemType())
                            .itemCategory(mi.getItemCategory())
                            .status(mi.getStatus())
                            .nextDueAt(mi.getNextDueAt())
                            .daysLate(daysLate)
                            .riskLevel(risk)
                            .build();
                })
                .sorted((a, b) -> RiskHeuristics.attentionComparator().compare(
                        new RiskHeuristics.AttentionItem(a.itemId(), a.itemType(), a.itemCategory(), a.status(), a.nextDueAt(), a.daysLate(), a.riskLevel()),
                        new RiskHeuristics.AttentionItem(b.itemId(), b.itemType(), b.itemCategory(), b.status(), b.nextDueAt(), b.daysLate(), b.riskLevel())
                ))
                .limit(limitAttention)
                .toList();

        // Calendar
        List<MaintenanceItem> upcoming = itemRepo.findUpcoming(orgId, today, endSoon);
        Map<LocalDate, List<Long>> byDateIds = upcoming.stream()
                .filter(mi -> mi.getNextDueAt() != null && !mi.getNextDueAt().isBefore(today) && !mi.getNextDueAt().isAfter(endSoon))
                .collect(Collectors.groupingBy(MaintenanceItem::getNextDueAt, Collectors.mapping(MaintenanceItem::getId, Collectors.toList())));

        List<DashboardResponse.CalendarDay> calendar = byDateIds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> DashboardResponse.CalendarDay.builder()
                        .date(e.getKey())
                        .count(e.getValue().size())
                        .itemIds(e.getValue().stream().limit(20).toList())
                        .build())
                .toList();

        // Breakdowns
        Map<ItemStatus, Long> byStatus = new EnumMap<>(ItemStatus.class);
        itemRepo.countByStatus(orgId).forEach(p -> byStatus.put(ItemStatus.valueOf(p.getStatus()), p.getCnt()));

        Map<ItemCategory, Long> byCategory = new EnumMap<>(ItemCategory.class);
        itemRepo.countByCategory(orgId).forEach(p -> byCategory.put(ItemCategory.valueOf(p.getItemCategory()), p.getCnt()));

        List<DashboardResponse.ItemTypeCount> byItemType = itemRepo.topByItemType(orgId, org.springframework.data.domain.PageRequest.of(0, 10))
                .stream().map(p -> DashboardResponse.ItemTypeCount.builder().itemType(p.getItemType()).count(p.getCnt()).build()).toList();

        DashboardResponse.Breakdowns breakdowns = DashboardResponse.Breakdowns.builder()
                .byStatus(byStatus)
                .byCategory(byCategory)
                .byItemType(byItemType)
                .build();

        // Quick actions
        List<DashboardResponse.QuickAction> quickActions = buildQuickActions(overdueCount, nearDueCount, dueSoonCount, daysAhead);

        // AI
        DashboardResponse.AiBlock ai = buildAi(orgId, props.enabled(), daysAhead, nearDueThresholdDays, kpis, attentionList, calendar, breakdowns);

        return DashboardResponse.builder()
                .kpis(kpis)
                .attentionNow(attentionList)
                .calendar(calendar)
                .breakdowns(breakdowns)
                .quickActions(quickActions)
                .ai(ai)
                .build();
    }

    private List<DashboardResponse.QuickAction> buildQuickActions(long overdueCount, long nearDueCount, long dueSoonCount, int daysAhead) {
        List<DashboardResponse.QuickAction> list = new ArrayList<>();
        if (overdueCount > 0) {
            list.add(DashboardResponse.QuickAction.builder()
                    .type("OPEN_LIST")
                    .label("Resolver itens vencidos")
                    .filter(Map.of("status", "OVERDUE"))
                    .build());
        }
        if (nearDueCount > 0) {
            list.add(DashboardResponse.QuickAction.builder()
                    .type("OPEN_LIST")
                    .label("Ver itens vencendo em breve")
                    .filter(Map.of("status", "NEAR_DUE"))
                    .build());
        }
        if (dueSoonCount > 0 && overdueCount == 0) {
            list.add(DashboardResponse.QuickAction.builder()
                    .type("OPEN_LIST")
                    .label("Planejar próximos vencimentos")
                    .filter(Map.of("dueWithinDays", daysAhead))
                    .build());
        }
        list.add(DashboardResponse.QuickAction.builder()
                .type("OPEN_SUPPLIERS")
                .label("Buscar fornecedores próximos")
                .radiusKm(20)
                .build());
        return list;
    }

    private DashboardResponse.AiBlock buildAi(String orgId,
                                              boolean enabled,
                                              int daysAhead,
                                              int nearDueDays,
                                              DashboardResponse.Kpis kpis,
                                              List<DashboardResponse.AttentionItem> attention,
                                              List<DashboardResponse.CalendarDay> calendar,
                                              DashboardResponse.Breakdowns breakdowns) {
        if (!enabled || chatClient.isEmpty()) return null;
        String key = orgId + ":" + daysAhead + ":" + nearDueDays;
        DashboardResponse.AiBlock cached = aiCache.getIfPresent(key);
        if (cached != null) return cached;

        StringBuilder ctx = new StringBuilder();
        ctx.append("KPIs: total=").append(kpis.itemsTotal())
                .append(", overdue=").append(kpis.overdueCount())
                .append(", nearDue=").append(kpis.nearDueCount())
                .append(", dueSoon=").append(kpis.dueSoonCount())
                .append(", maintThisMonth=").append(kpis.maintenancesThisMonth())
                .append(", avgDaysToResolve=").append(kpis.avgDaysToResolve())
                .append(", complianceScore=").append(kpis.complianceScore()).append("\n");
        ctx.append("Top atenção:");
        attention.forEach(a -> ctx.append("\n - ").append(a.itemType()).append("/").append(a.itemCategory()).append(" ")
                .append(a.status()).append(" due=").append(a.nextDueAt()).append(" late=").append(a.daysLate()).append(" risk=").append(a.riskLevel()));

        String prompt = "Gere 3 bullets curtos com destaques e 1 próxima melhor ação, em português do Brasil, " +
                "considerando os dados do dashboard a seguir (seja objetivo e prático).";

        var resp = chatClient.get().prompt()
                .user(u -> u.text(prompt + "\n\n" + ctx))
                .call()
                .content();

        // Simple post-process: split lines; take first 3 for highlights and last one for next action if possible
        List<String> lines = Arrays.stream(resp.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        List<String> highlights = lines.stream().limit(3).toList();
        String nextBest = lines.size() > 3 ? lines.get(3) : (lines.isEmpty() ? "" : lines.getLast());

        DashboardResponse.AiBlock block = DashboardResponse.AiBlock.builder()
                .highlights(highlights)
                .nextBestAction(nextBest)
                .build();
        aiCache.put(key, block);
        return block;
    }
}
