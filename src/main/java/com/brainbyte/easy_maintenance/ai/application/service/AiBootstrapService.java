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

@Slf4j
@Service
@RequiredArgsConstructor
public class AiBootstrapService {

    private final AiPromptTemplateRepository templateRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEMPLATE_KEY = "ONBOARDING_BOOTSTRAP";

    public AiBootstrapPreviewResponse preview(AiBootstrapPreviewRequest request) {
        String dbCompanyType = request.getCompanyType().getDbValue();

        AiPromptTemplate template = templateRepository.findLatestActive(TEMPLATE_KEY, dbCompanyType)
                .orElseThrow(() -> new RuntimeException("Template não encontrado para " + dbCompanyType));

        String userPrompt = template.getUserPrompt();
        if (StringUtils.isNotBlank(request.getDescription())) {
            userPrompt += "\nContexto adicional do usuário: " + request.getDescription();
        }

        try {
            String jsonResponse = chatClient.prompt()
                    .system(template.getSystemPrompt())
                    .user(userPrompt)
                    .options(org.springframework.ai.openai.OpenAiChatOptions.builder()
                            .temperature(template.getTemperature() != null ? template.getTemperature().doubleValue() : 0.3)
                            .maxTokens(template.getMaxTokens() != null ? template.getMaxTokens() : 1000)
                            .build())
                    .call()
                    .content();

            AiBootstrapPreviewResponse response = objectMapper.readValue(jsonResponse, AiBootstrapPreviewResponse.class);
            response.setUsedAi(true);
            response.setCompanyType(dbCompanyType);
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
}
