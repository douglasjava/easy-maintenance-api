package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

import com.brainbyte.easy_maintenance.commons.exceptions.IAException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class FallbackAiProvider implements AiProvider {

    private final AiProvider primary;
    private final AiProvider fallback;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            return primary.chat(systemPrompt, userPrompt);
        } catch (Exception e) {
            log.warn("Primary AI provider failed, falling back to OpenAI. Error: {}", e.getMessage());
            try {
                return fallback.chat(systemPrompt, userPrompt);
            } catch (Exception ex) {
                log.error("Both AI providers failed. Fallback error: {}", ex.getMessage());
                throw new IAException("All AI providers failed", ex);
            }
        }
    }
}
