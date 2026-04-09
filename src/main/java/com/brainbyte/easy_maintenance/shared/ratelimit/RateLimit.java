package com.brainbyte.easy_maintenance.shared.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Aplica rate limiting ao endpoint anotado.
 * Os limites são lidos de {@code rate-limit.limits.<value>.*} em application.properties.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    /** Nome do bucket — deve corresponder a uma chave em rate-limit.limits. */
    String value();
}
