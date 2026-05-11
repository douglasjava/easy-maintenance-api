package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;

@Slf4j
@RequiredArgsConstructor
public class OpenAiAiProvider implements AiProvider {

    private final ChatClient chatClient;

    @Override
    public AiChatResult chat(String systemPrompt, String userPrompt) {
        log.info("Using OpenAI Provider");
        var spec = chatClient.prompt();
        if (systemPrompt != null) {
            spec.system(systemPrompt);
        }
        var chatResponse = spec.user(userPrompt).call().chatResponse();
        String content = chatResponse.getResult().getOutput().getContent();
        int tokens = extractTokens(chatResponse.getMetadata().getUsage());
        log.debug("OpenAI tokens used: {}", tokens);
        return AiChatResult.of(content, tokens);
    }

    private static int extractTokens(Usage usage) {
        if (usage == null || usage.getTotalTokens() == null) return 0;
        return (int) Math.min(usage.getTotalTokens(), Integer.MAX_VALUE);
    }
}
