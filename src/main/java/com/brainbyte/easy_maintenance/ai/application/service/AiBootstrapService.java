package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewResponse;
import com.brainbyte.easy_maintenance.ai.domain.AiPromptTemplate;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiPromptTemplateRepository;
import com.brainbyte.easy_maintenance.ai.infrastructure.provider.AiProvider;
import com.brainbyte.easy_maintenance.ai.mapper.IAiBootstrapMapper;
import com.brainbyte.easy_maintenance.assets.domain.ItemTypes;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.ItemTypesRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceItemRepository;
import com.brainbyte.easy_maintenance.catalog_norms.domain.Norm;
import com.brainbyte.easy_maintenance.catalog_norms.infrastructure.persistence.NormRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.commons.helper.NormalizerUtil;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiBootstrapService {

    private final AiPromptTemplateRepository templateRepository;
    private final AiProvider aiProvider;
    private final ItemTypesRepository itemTypesRepository;
    private final NormRepository normRepository;
    private final MaintenanceItemRepository maintenanceItemRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEMPLATE_KEY = "ONBOARDING_BOOTSTRAP";

    public AiBootstrapPreviewResponse preview(AiBootstrapPreviewRequest request) {
        log.info("Iniciando AiBootstrapPreviewRequest {}", request);

        String dbCompanyType = request.getCompanyType().getDbValue();

        AiPromptTemplate template = templateRepository.findLatestActive(TEMPLATE_KEY, dbCompanyType)
                .orElseThrow(() -> new NotFoundException(String.format("Template não encontrado para %s", dbCompanyType)));

        String userPrompt = template.getUserPrompt();
        if (StringUtils.isNotBlank(request.getDescription())) {
            userPrompt += "\nContexto adicional do usuário: " + request.getDescription();
        }

        // 1) Anexa contrato de saída (JSON canônico + exemplo)
        userPrompt = appendOutputContract(userPrompt);

        try {

            String jsonResponse = aiProvider.chat(template.getSystemPrompt(), userPrompt);

            // 2) Higieniza/extrai só o JSON
            String jsonOnly = extractJsonObject(jsonResponse);

            AiBootstrapPreviewResponse response = objectMapper.readValue(jsonOnly, AiBootstrapPreviewResponse.class);
            response.setUsedAi(true);
            response.setCompanyType(dbCompanyType);

            log.info("AiBootstrapPreviewResponse {}", response);

            return response;

        } catch (Exception e) {
            log.error("Erro ao gerar preview de onboarding via IA para {}: {}", dbCompanyType, e.getMessage());
            return AiBootstrapPreviewResponse.builder()
                    .usedAi(false)
                    .companyType(dbCompanyType)
                    .items(new ArrayList<>())
                    .build();
        }
    }

    @Transactional
    public AiBootstrapApplyResponse apply(AiBootstrapApplyRequest request) {
        log.info("Applying AI Bootstrap for {} items", request.getItems().size());
        String organizationCode = TenantContext.get().orElseThrow(() -> new TenantException(HttpStatus.FORBIDDEN, "Item does not belong to tenant"));

        List<AiBootstrapApplyResponse.CreatedItem> created = new ArrayList<>();
        List<AiBootstrapApplyResponse.FailedItem> failed = new ArrayList<>();

        for (var item : request.getItems()) {
            try {
                Long itemId = processItem(item, organizationCode);
                created.add(AiBootstrapApplyResponse.CreatedItem.builder()
                        .localId(item.getLocalId())
                        .itemId(itemId)
                        .build());
            } catch (Exception e) {
                log.error("Error processing item {}: {}", item.getLocalId(), e.getMessage());
                failed.add(AiBootstrapApplyResponse.FailedItem.builder()
                        .localId(item.getLocalId())
                        .message(e.getMessage())
                        .build());
            }
        }

        return AiBootstrapApplyResponse.builder()
                .created(created)
                .failed(failed)
                .build();
    }

    private Long processItem(AiBootstrapApplyRequest.BootstrapApplyItem item, String organizationCode) {
        // 1. Garantir item_types
        ItemTypes itemTypes = ensureItemType(item.getItemType());
        log.info("Retorno ItemTypes {}", itemTypes);

        // 2. Resolver Norm
        Long normId = resolveNorm(item);

        // 3. Criar MaintenanceItem
        MaintenanceItem maintenanceItem = IAiBootstrapMapper.INSTANCE.toMaintenanceItem(item, organizationCode, normId);
        maintenanceItem = maintenanceItemRepository.save(maintenanceItem);

        return maintenanceItem.getId();
    }

    private ItemTypes ensureItemType(String itemTypeName) {
        String normalized = NormalizerUtil.normalize(itemTypeName);

        return itemTypesRepository.findByNormalizedName(normalized)
                .orElseGet(() -> createItemType(itemTypeName, normalized));
    }

    private ItemTypes createItemType(String name, String normalized) {
        ItemTypes newItemType = ItemTypes.builder()
                .name(name)
                .normalizedName(normalized)
                .status(Status.ACTIVE)
                .createdAt(Instant.now())
                .build();

        return itemTypesRepository.save(newItemType);
    }

    private Long resolveNorm(AiBootstrapApplyRequest.BootstrapApplyItem item) {
        // Tenta achar norma existente equivalente
        List<Norm> existingNorms = normRepository.findByItemType(item.getItemType());

        Optional<Norm> matchingNorm = existingNorms.stream()
                .filter(n -> n.getPeriodQty().equals(item.getMaintenance().getPeriodQty()) &&
                        n.getPeriodUnit().name().equalsIgnoreCase(item.getMaintenance().getPeriodUnit()) &&
                        n.getAuthority().equals("AI_BOOTSTRAP"))
                .findFirst();

        if (matchingNorm.isPresent()) {
            return matchingNorm.get().getId();
        }

        // Se não encontrar, cria uma nova norma
        Norm newNorm = IAiBootstrapMapper.INSTANCE.toNorm(item);
        newNorm = normRepository.save(newNorm);
        return newNorm.getId();
    }

    private String appendOutputContract(String basePrompt) {
        AiBootstrapPreviewResponse example = AiBootstrapPreviewResponse.builder()
                .items(List.of(
                        AiBootstrapPreviewResponse.BootstrapItem.builder()
                                .itemType("ELEVADOR")
                                .category("SEGURANCA")
                                .criticality("ALTA")
                                .maintenance(AiBootstrapPreviewResponse.MaintenancePreview.builder()
                                        .norm("ABNT/IT (quando aplicável)")
                                        .periodUnit("MESES")      // DIAS | MESES | ANUAL
                                        .periodQty(1)
                                        .toleranceDays(5)
                                        .notes("Descrição breve e técnica da manutenção")
                                        .build())
                                .build()
                ))
                .build();

        String exampleJson;
        try {
            exampleJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(example);
        } catch (Exception e) {
            exampleJson = "{\"items\":[{\"itemType\":\"ELEVADOR\",\"category\":\"SEGURANCA\",\"criticality\":\"ALTA\",\"maintenance\":{\"norm\":\"ABNT/IT (quando aplicável)\",\"periodUnit\":\"MESES\",\"periodQty\":1,\"toleranceDays\":5,\"notes\":\"...\"}}]}";
        }

        return basePrompt + "\n\n" +
                "IMPORTANTE (formato de saída):\n" +
                "1) Retorne APENAS um JSON válido (NÃO use markdown, NÃO use ```).\n" +
                "2) Não inclua texto antes/depois do JSON.\n" +
                "3) O JSON deve conter APENAS os campos do exemplo (não adicione campos extras).\n" +
                "4) Campos obrigatórios: items[].itemType, items[].category, items[].criticality, items[].maintenance.\n" +
                "5) periodUnit deve ser um destes valores: DIAS, MESES, ANUAL.\n" +
                "6) Se não souber algum campo, use null.\n" +
                "\n" +
                "LIMITAÇÕES (para evitar respostas longas):\n" +
                "7) Retorne no máximo 8 itens em items (selecione os mais relevantes).\n" +
                "8) notes deve ter no máximo 200 caracteres (resumo curto e técnico).\n" +
                "9) norm deve ter no máximo 120 caracteres.\n" +
                "10) Não invente itens fora do contexto do tipo de empresa; selecione apenas os itens aplicáveis.\n" +
                "\n" +
                "EXEMPLO DO JSON (siga exatamente esta estrutura):\n" +
                exampleJson;
    }


    /**
     * Remove casos comuns: markdown ```json ... ```, texto antes/depois, etc.
     * Estratégia: pegar do primeiro '{' até o último '}'.
     */
    private String extractJsonObject(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();

        // remove fences se vier em markdown
        if (s.startsWith("```")) {
            s = s.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }

        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1).trim();
        }
        return s;
    }

}
