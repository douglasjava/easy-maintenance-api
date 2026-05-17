package com.brainbyte.easy_maintenance.billing.error;

public enum RefusalBucket {
    /** Temporary gateway/network failure — safe to retry with backoff. */
    TRANSIENT,
    /** User must intervene (expired card, invalid data, PIX key gone, etc.). */
    USER_ACTION,
    /** Definitive refusal (fraud, blocked account, repeated declines). */
    HARD_FAIL,
    /** Informational only (dispute, chargeback). Log + light notification. */
    INFO,
    /** Unknown or unmapped error code. */
    UNKNOWN
}
