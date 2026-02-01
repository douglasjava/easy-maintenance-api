package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.prompt.PromptBuilder;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSummaryResponse;
import com.brainbyte.easy_maintenance.ai.infrastructure.provider.AiProvider;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.catalog_norms.application.service.NormService;
import com.brainbyte.easy_maintenance.catalog_norms.application.dto.NormDTO;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final MaintenanceItemRepository itemRepository;
    private final NormService normService;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiSummaryResponse getSummary(boolean pretty) {
        String orgId = TenantContext.get().orElseThrow();
        long total = itemRepository.countByOrganizationCode(orgId);
        long ok = itemRepository.countByOrganizationCodeAndStatus(orgId, ItemStatus.OK);
        long near = itemRepository.countByOrganizationCodeAndStatus(orgId, ItemStatus.NEAR_DUE);
        long over = itemRepository.countByOrganizationCodeAndStatus(orgId, ItemStatus.OVERDUE);

        AiSummaryResponse.AiSummaryResponseBuilder builder = AiSummaryResponse.builder()
                .total(total)
                .ok(ok)
                .nearDue(near)
                .overdue(over)
                .usedAi(false);

        if (pretty) {
            try {
                String prompt = PromptBuilder.beautifySummaryPrompt(total, ok, near, over);
                String content = aiProvider.chat(null, prompt);
                builder.prettyText(Optional.ofNullable(content).orElse(""));
                builder.usedAi(true);
            } catch (Exception e) {
                log.warn("AI pretty summary failed, falling back without pretty text", e);
                builder.prettyText(null);
            }
        }

        return builder.build();
    }

    public AiAssistantResponse assistant(AiAssistantRequest req) {
        String orgId = TenantContext.get().orElseThrow();
        // Build minimal context: counts + next due items + norms for optional itemType
        String context = buildContext(orgId, req.getItemType());
        String prompt = PromptBuilder.assistantPrompt(req.getQuestion(), context);
        String answer = aiProvider.chat(null, prompt);
        return AiAssistantResponse.builder()
                .answer(answer)
                .usedAi(true)
                .build();
    }

    public AiSuggestItemResponse suggestItem(AiSuggestItemRequest req) {
        // orgId is not strictly needed for suggestion, but keep for future context if provided
        String prompt = PromptBuilder.suggestItemPrompt(req.getDescription(), req.getContextItemType());
        String json = aiProvider.chat(null, prompt);
        try {
            AiSuggestItemResponse parsed = objectMapper.readValue(json, AiSuggestItemResponse.class);
            parsed.setUsedAi(true);
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse AI JSON for suggest-item. Returning minimal response.");
            return AiSuggestItemResponse.builder()
                    .itemType(req.getContextItemType())
                    .tags(List.of())
                    .usedAi(false)
                    .build();
        }
    }

    private String buildContext(String orgId, String itemType) {
        StringBuilder sb = new StringBuilder();
        long total = itemRepository.countByOrganizationCode(orgId);
        long ok = itemRepository.countByOrganizationCodeAndStatus(orgId, ItemStatus.OK);
        long near = itemRepository.countByOrganizationCodeAndStatus(orgId, ItemStatus.NEAR_DUE);
        long over = itemRepository.countByOrganizationCodeAndStatus(orgId, ItemStatus.OVERDUE);
        sb.append("Itens: total=").append(total)
                .append(", ok=").append(ok)
                .append(", perto=").append(near)
                .append(", vencidas=").append(over)
                .append("\n");

        // Next due within top 5
        Page<MaintenanceItem> page = itemRepository.findByOrganizationCode(orgId,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "nextDueAt")));
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        String upcoming = page.getContent().stream()
                .filter(i -> i.getNextDueAt() != null)
                .map(i -> i.getItemType() + ": " + fmt.format(i.getNextDueAt()) + " (" + i.getStatus() + ")")
                .collect(Collectors.joining(", "));
        if (!upcoming.isBlank()) {
            sb.append("Próximos vencimentos: ").append(upcoming).append("\n");
        }

        // Norms
        List<NormDTO.NormResponse> norms = itemType != null && !itemType.isBlank() ?
                normService.findByItemType(itemType) : normService.findAll();
        String normsStr = norms.stream()
                .limit(5)
                .map(n -> n.itemType() + "/" + n.authority() + ": periodo=" + n.periodQty() + " " + n.periodUnit())
                .collect(Collectors.joining(", "));
        if (!normsStr.isBlank()) {
            sb.append("Normas: ").append(normsStr);
        }
        return sb.toString();
    }
}
