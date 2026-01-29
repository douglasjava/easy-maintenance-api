package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.commons.properties.MailerSendProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(MailerSendProperties.class)
@RequiredArgsConstructor
public class MailerSendConfig {

    private final MailerSendProperties mailerSendProperties;

    @Bean
    public WebClient mailerSendWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(mailerSendProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + mailerSendProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
