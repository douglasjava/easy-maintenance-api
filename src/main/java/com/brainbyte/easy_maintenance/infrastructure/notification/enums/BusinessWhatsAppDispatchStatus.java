package com.brainbyte.easy_maintenance.infrastructure.notification.enums;

public enum BusinessWhatsAppDispatchStatus {
    PENDING,
    // Fora do horário comercial (8h-20h Brasília) — aguardando o WhatsAppDeferredSendJob (TASK-131)
    // enviar assim que entrar na janela permitida. Nunca descartado.
    PENDING_HOURS_WINDOW,
    SENT,
    FAILED,
    SKIPPED_OPT_OUT,
    SKIPPED_INVALID_RECIPIENT,
    SKIPPED_QUOTA,
    SKIPPED_RATE_LIMIT
}
