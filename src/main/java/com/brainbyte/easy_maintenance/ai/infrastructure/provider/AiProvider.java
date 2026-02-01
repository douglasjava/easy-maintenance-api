package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

public interface AiProvider {

    String chat(String systemPrompt, String userPrompt);

}
