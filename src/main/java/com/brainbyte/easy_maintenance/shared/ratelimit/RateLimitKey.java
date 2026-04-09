package com.brainbyte.easy_maintenance.shared.ratelimit;

public enum RateLimitKey {
    /** Limita por IP do cliente (ex: login, forgot-password). */
    IP,
    /** Limita por ID do usuário autenticado (ex: endpoints de IA). */
    USER_ID
}
