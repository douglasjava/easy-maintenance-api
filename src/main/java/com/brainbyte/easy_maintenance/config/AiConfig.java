package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.ai.infrastructure.provider.AiProvider;
import com.brainbyte.easy_maintenance.ai.infrastructure.provider.DeepSeekAiProvider;
import com.brainbyte.easy_maintenance.ai.infrastructure.provider.FallbackAiProvider;
import com.brainbyte.easy_maintenance.ai.infrastructure.provider.OpenAiAiProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${spring.ai.deepseek.api-key}")
    private String deepSeekApiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepSeekBaseUrl;

    @Value("${spring.ai.chat.options.model:deepseek-chat}")
    private String deepSeekModel;

    @Value("${spring.ai.chat.options.temperature:0.2}")
    private Double deepSeekTemperature;

    @Value("${spring.ai.chat.options.max-tokens:1200}")
    private Integer deepSeekMaxTokens;

    @Bean
    public ChatModel customOpenAiChatModel() {
        return new OpenAiChatModel(new OpenAiApi(openAiApiKey), 
            OpenAiChatOptions.builder()
                .model(openAiModel)
                .temperature(1.0)
                .build());
    }

    @Bean
    public ChatModel customDeepSeekChatModel() {
        return new OpenAiChatModel(new OpenAiApi(deepSeekBaseUrl, deepSeekApiKey), 
            OpenAiChatOptions.builder()
                .model(deepSeekModel)
                .temperature(deepSeekTemperature)
                .maxTokens(deepSeekMaxTokens)
                .build());
    }

    @Bean
    public AiProvider openAiAiProvider(@Qualifier("customOpenAiChatModel") ChatModel chatModel) {
        return new OpenAiAiProvider(ChatClient.builder(chatModel)
            .defaultSystem("""
                 Você é um assistente especializado em gestão de manutenção
                 (hospitalar, condomínios, escolas, indústrias e escritórios) no Brasil.

                 Responda sempre em português, de forma objetiva, prática e orientada à ação.

                 Ajude o usuário a:
                 - organizar manutenções preventivas e corretivas
                 - identificar riscos operacionais e regulatórios
                 - priorizar tarefas e prazos críticos

                 Quando o tema envolver normas, legislação ou requisitos técnicos,
                 indique possíveis referências, deixando claro que a aplicação pode variar
                 conforme o contexto e o órgão regulador.
                 """)
            .build());
    }

    @Bean
    public AiProvider deepSeekAiProvider(@Qualifier("customDeepSeekChatModel") ChatModel chatModel) {
        return new DeepSeekAiProvider(ChatClient.builder(chatModel)
            .defaultSystem("""
                 Você é um assistente especializado em gestão de manutenção
                 (hospitalar, condomínios, escolas, indústrias e escritórios) no Brasil.

                 Responda sempre em português, de forma objetiva, prática e orientada à ação.

                 Ajude o usuário a:
                 - organizar manutenções preventivas e corretivas
                 - identificar riscos operacionais e regulatórios
                 - priorizar tarefas e prazos críticos

                 Quando o tema envolver normas, legislação ou requisitos técnicos,
                 indique possíveis referências, deixando claro que a aplicação pode variar
                 conforme o contexto e o órgão regulador.
                 """)
            .build());
    }

    @Bean
    @Primary
    public AiProvider aiProvider(AiProvider deepSeekAiProvider, AiProvider openAiAiProvider) {
        return new FallbackAiProvider(deepSeekAiProvider, openAiAiProvider);
    }

}
