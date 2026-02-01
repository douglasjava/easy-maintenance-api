package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

@Slf4j
@RequiredArgsConstructor
public class OpenAiAiProvider implements AiProvider {

    private final ChatClient chatClient;

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        log.info("Using OpenAI Provider");
        var spec = chatClient.prompt();
        if (systemPrompt != null) {
            spec.system(systemPrompt);
        }
        return spec.user(userPrompt)
                .call()
                .content();
    }

}
