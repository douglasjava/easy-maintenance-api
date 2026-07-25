package com.brainbyte.easy_maintenance.config;

import com.brainbyte.easy_maintenance.commons.properties.ResendProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(ResendProperties.class)
@RequiredArgsConstructor
public class ResendConfig {

    private final ResendProperties resendProperties;

    @Bean
    public WebClient resendWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(resendProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + resendProperties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
