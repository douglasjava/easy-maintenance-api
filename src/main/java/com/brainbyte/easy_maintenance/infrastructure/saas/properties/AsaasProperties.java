package com.brainbyte.easy_maintenance.infrastructure.saas.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "asaas")
public record AsaasProperties(
        String baseUrl,
        String apiKey,
        String webhookToken,
        String checkoutSuccessUrl,
        String checkoutCancelUrl,
        String checkoutExpiredUrl,
        Integer checkoutMinutesToExpire
) {}
