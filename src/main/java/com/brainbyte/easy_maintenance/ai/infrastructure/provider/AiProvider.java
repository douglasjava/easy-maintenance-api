package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

public interface AiProvider {

    AiChatResult chat(String systemPrompt, String userPrompt);

}
