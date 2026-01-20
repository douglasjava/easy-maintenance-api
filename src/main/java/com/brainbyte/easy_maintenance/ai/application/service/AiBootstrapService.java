package com.brainbyte.easy_maintenance.ai.application.service;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewResponse;
import com.brainbyte.easy_maintenance.ai.domain.AiPromptTemplate;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiPromptTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiBootstrapService {

    private final AiPromptTemplateRepository templateRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEMPLATE_KEY = "ONBOARDING_BOOTSTRAP";

    public AiBootstrapPreviewResponse preview(AiBootstrapPreviewRequest request) {
        log.info("Iniciando AiBootstrapPreviewRequest {}", request);

        String dbCompanyType = request.getCompanyType().getDbValue();

        AiPromptTemplate template = templateRepository.findLatestActive(TEMPLATE_KEY, dbCompanyType)
                .orElseThrow(() -> new RuntimeException("Template não encontrado para " + dbCompanyType));

        String userPrompt = template.getUserPrompt();
        if (StringUtils.isNotBlank(request.getDescription())) {
            userPrompt += "\nContexto adicional do usuário: " + request.getDescription();
        }

        // 1) Anexa contrato de saída (JSON canônico + exemplo)
        userPrompt = appendOutputContract(userPrompt);

        try {
            String jsonResponse = chatClient.prompt()
                    .system(template.getSystemPrompt())
                    .user(userPrompt)
                    .call()
                    .content();

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
                "1) Retorne APENAS um JSON válido, sem markdown, sem texto antes/depois.\n" +
                "2) O JSON deve conter APENAS os campos do exemplo (não adicione campos extras).\n" +
                "3) Campos obrigatórios: items[].itemType, items[].category, items[].criticality, items[].maintenance.\n" +
                "4) periodUnit deve ser um destes valores: DIAS, MESES, ANUAL.\n" +
                "5) Se não souber algum campo, use null.\n" +
                "6) items deve ser uma lista (mesmo que tenha 1 item).\n\n" +
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
