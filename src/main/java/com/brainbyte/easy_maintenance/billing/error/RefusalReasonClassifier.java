package com.brainbyte.easy_maintenance.billing.error;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps Asaas refusal/failure codes to a {@link RefusalBucket} and records
 * a Micrometer counter {@code easy_billing.refusal.bucket.count{bucket=...}}.
 *
 * <p>Default mappings cover the most common Asaas codes. Any entry in
 * {@code billing.refusal.overrides.*} replaces the default at startup.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefusalReasonClassifier {

    static final String METRIC_NAME = "easy_billing.refusal.bucket.count";

    private final MeterRegistry meterRegistry;
    private final RefusalClassificationProperties properties;

    private final Map<String, RefusalBucket> effectiveMap = new HashMap<>();

    private static final Map<String, RefusalBucket> DEFAULTS = new HashMap<>();

    static {
        // TRANSIENT — temporary gateway/network issues
        DEFAULTS.put("BANK_NOT_RESPONDING",     RefusalBucket.TRANSIENT);
        DEFAULTS.put("TIMEOUT",                 RefusalBucket.TRANSIENT);
        DEFAULTS.put("GATEWAY_ERROR",           RefusalBucket.TRANSIENT);
        DEFAULTS.put("PIX_EXPIRED",             RefusalBucket.TRANSIENT);
        DEFAULTS.put("PROCESSING_ERROR",        RefusalBucket.TRANSIENT);

        // USER_ACTION — user must intervene
        DEFAULTS.put("INSUFFICIENT_FUNDS",      RefusalBucket.USER_ACTION);
        DEFAULTS.put("INVALID_CARD",            RefusalBucket.USER_ACTION);
        DEFAULTS.put("EXPIRED_CARD",            RefusalBucket.USER_ACTION);
        DEFAULTS.put("DO_NOT_HONOR",            RefusalBucket.USER_ACTION);
        DEFAULTS.put("AUTHORIZATION_DENIED",    RefusalBucket.USER_ACTION);
        DEFAULTS.put("PIX_KEY_NOT_FOUND",       RefusalBucket.USER_ACTION);
        DEFAULTS.put("INVALID_CVV",             RefusalBucket.USER_ACTION);

        // HARD_FAIL — definitive, no retry
        DEFAULTS.put("FRAUD_DETECTED",          RefusalBucket.HARD_FAIL);
        DEFAULTS.put("CARD_STOLEN",             RefusalBucket.HARD_FAIL);
        DEFAULTS.put("CARD_LOST",               RefusalBucket.HARD_FAIL);
        DEFAULTS.put("SECURITY_VIOLATION",      RefusalBucket.HARD_FAIL);

        // INFO — dispute / chargeback, no state change
        DEFAULTS.put("CHARGEBACK",              RefusalBucket.INFO);
        DEFAULTS.put("DISPUTE",                 RefusalBucket.INFO);
    }

    @PostConstruct
    void init() {
        effectiveMap.putAll(DEFAULTS);

        properties.getOverrides().forEach((code, bucketName) -> {
            try {
                RefusalBucket bucket = RefusalBucket.valueOf(bucketName.toUpperCase());
                effectiveMap.put(code.toUpperCase(), bucket);
                log.info("[RefusalClassifier] Override applied: {} → {}", code, bucket);
            } catch (IllegalArgumentException e) {
                log.warn("[RefusalClassifier] Invalid bucket name '{}' for code '{}', ignoring override.", bucketName, code);
            }
        });
    }

    /**
     * Classifies the given Asaas error/failure code and increments the
     * corresponding Micrometer counter.
     *
     * @param failureCode Asaas failure code (case-insensitive). Null-safe.
     * @return the matching {@link RefusalBucket}, never {@code null}.
     */
    public RefusalBucket classify(String failureCode) {
        RefusalBucket bucket = failureCode == null
                ? RefusalBucket.UNKNOWN
                : effectiveMap.getOrDefault(failureCode.toUpperCase(), RefusalBucket.UNKNOWN);

        meterRegistry.counter(METRIC_NAME, "bucket", bucket.name()).increment();

        log.info("[RefusalClassifier] code='{}' → bucket={}", failureCode, bucket);
        return bucket;
    }
}
