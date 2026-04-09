package com.brainbyte.easy_maintenance.shared.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private Map<String, LimitConfig> limits = new HashMap<>();

    @Data
    public static class LimitConfig {
        /** Número máximo de tokens no bucket. */
        private long capacity;
        /** Intervalo em segundos para recarregar todos os tokens. */
        private long refillPeriodSeconds;
        /** Estratégia de chave: IP ou USER_ID. */
        private RateLimitKey key = RateLimitKey.IP;
    }
}
