package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

import com.brainbyte.easy_maintenance.commons.exceptions.IAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
public class DeepSeekAiProvider implements AiProvider {

    private final ChatClient chatClient;

    @Override
    public AiChatResult chat(String systemPrompt, String userPrompt) {
        log.info("Using DeepSeek Provider");
        try {
            var spec = chatClient.prompt();
            if (systemPrompt != null) {
                spec.system(systemPrompt);
            }
            var chatResponse = spec.user(userPrompt).call().chatResponse();
            String rawContent = chatResponse.getResult().getOutput().getContent();
            int tokens = extractTokens(chatResponse.getMetadata().getUsage());

            if (!StringUtils.hasText(rawContent)) {
                throw new IAException("DeepSeek returned empty response");
            }

            String cleaned = stripMarkdownCodeFence(rawContent);
            String jsonOnly = extractJsonObject(cleaned);

            if (userPrompt.toLowerCase().contains("json") && !isValidJson(jsonOnly)) {
                throw new IAException("DeepSeek returned invalid JSON");
            }

            String content = expectsJson(userPrompt) ? jsonOnly : rawContent;
            log.debug("DeepSeek tokens used: {}", tokens);
            return AiChatResult.of(content, tokens);

        } catch (Exception e) {
            log.error("DeepSeek call failed: {}", e.getMessage());
            throw new IAException("DeepSeek provider error", e);
        }
    }

    private static int extractTokens(Usage usage) {
        if (usage == null || usage.getTotalTokens() == null) return 0;
        return (int) Math.min(usage.getTotalTokens(), Integer.MAX_VALUE);
    }

    private boolean isValidJson(String content) {
        try {
            String trimmed = content.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed);
                return true;
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(trimmed);
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean expectsJson(String prompt) {
        return prompt != null && prompt.toLowerCase().contains("json");
    }

    private String stripMarkdownCodeFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
        }
        return t;
    }

    private String extractJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) return s.substring(start, end + 1).trim();
        return s.trim();
    }
}
