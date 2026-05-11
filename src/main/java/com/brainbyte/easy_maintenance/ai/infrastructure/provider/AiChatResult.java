package com.brainbyte.easy_maintenance.ai.infrastructure.provider;

/**
 * Value returned by every {@link AiProvider#chat} call.
 * Carries both the text content and the actual token count reported by the API,
 * so callers can deduct the real cost from the user's monthly credit balance.
 */
public record AiChatResult(String content, int tokensUsed) {

    public static AiChatResult of(String content, int tokensUsed) {
        return new AiChatResult(content, tokensUsed);
    }

    public static AiChatResult noTokenData(String content) {
        return new AiChatResult(content, 0);
    }
}
