package com.brainbyte.easy_maintenance.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private final Cache<String, Bucket> bucketCache = Caffeine.newBuilder()
            .expireAfterAccess(2, TimeUnit.HOURS)
            .maximumSize(50_000)
            .build();

    /**
     * Consome um token do bucket identificado por {@code key}.
     * Lança {@link RateLimitException} se o limite for excedido.
     */
    public void tryConsume(String key, RateLimitProperties.LimitConfig config) {
        Bucket bucket = bucketCache.get(key, k -> buildBucket(config));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            throw new RateLimitException(retryAfterSeconds);
        }
    }

    private Bucket buildBucket(RateLimitProperties.LimitConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.getCapacity())
                .refillIntervally(config.getCapacity(), Duration.ofSeconds(config.getRefillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
