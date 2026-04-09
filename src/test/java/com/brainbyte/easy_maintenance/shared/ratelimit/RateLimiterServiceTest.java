package com.brainbyte.easy_maintenance.shared.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService service;
    private RateLimitProperties.LimitConfig config;

    @BeforeEach
    void setUp() {
        service = new RateLimiterService();
        config = new RateLimitProperties.LimitConfig();
        config.setCapacity(3);
        config.setRefillPeriodSeconds(60);
        config.setKey(RateLimitKey.IP);
    }

    @Test
    void shouldAllowRequestsWithinLimit() {
        assertDoesNotThrow(() -> service.tryConsume("test:ip:1.2.3.4", config));
        assertDoesNotThrow(() -> service.tryConsume("test:ip:1.2.3.4", config));
        assertDoesNotThrow(() -> service.tryConsume("test:ip:1.2.3.4", config));
    }

    @Test
    void shouldThrowRateLimitExceptionWhenLimitExceeded() {
        service.tryConsume("test:ip:9.9.9.9", config);
        service.tryConsume("test:ip:9.9.9.9", config);
        service.tryConsume("test:ip:9.9.9.9", config);

        RateLimitException ex = assertThrows(RateLimitException.class,
                () -> service.tryConsume("test:ip:9.9.9.9", config));

        assertTrue(ex.getRetryAfterSeconds() > 0, "Retry-After deve ser positivo");
    }

    @Test
    void shouldIsolateBucketsByKey() {
        // Chave A consome até o limite
        service.tryConsume("test:ip:A", config);
        service.tryConsume("test:ip:A", config);
        service.tryConsume("test:ip:A", config);
        assertThrows(RateLimitException.class, () -> service.tryConsume("test:ip:A", config));

        // Chave B não é afetada
        assertDoesNotThrow(() -> service.tryConsume("test:ip:B", config));
    }

    @Test
    void shouldIncludeRetryAfterInException() {
        service.tryConsume("test:ip:retry", config);
        service.tryConsume("test:ip:retry", config);
        service.tryConsume("test:ip:retry", config);

        RateLimitException ex = assertThrows(RateLimitException.class,
                () -> service.tryConsume("test:ip:retry", config));

        assertTrue(ex.getRetryAfterSeconds() >= 1);
        assertTrue(ex.getMessage().contains("seconds"));
    }
}
