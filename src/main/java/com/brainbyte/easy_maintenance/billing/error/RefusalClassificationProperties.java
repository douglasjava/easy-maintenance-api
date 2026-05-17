package com.brainbyte.easy_maintenance.billing.error;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows remapping Asaas error codes to buckets without rebuild.
 * Example in application.properties:
 *   billing.refusal.overrides.MY_NEW_CODE=USER_ACTION
 *   billing.refusal.overrides.BANK_NOT_RESPONDING=TRANSIENT
 */
@Component
@ConfigurationProperties(prefix = "billing.refusal")
public class RefusalClassificationProperties {

    private final Map<String, String> overrides = new HashMap<>();

    public Map<String, String> getOverrides() {
        return overrides;
    }
}
