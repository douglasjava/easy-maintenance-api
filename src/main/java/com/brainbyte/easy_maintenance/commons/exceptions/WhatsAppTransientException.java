package com.brainbyte.easy_maintenance.commons.exceptions;

/**
 * Falha transitória no envio de WhatsApp (5xx, timeout, erro de conexão, rate-limit da Meta) —
 * elegível para retry (ver resilience4j.retry.instances.whatsapp em application.properties).
 */
public class WhatsAppTransientException extends RuntimeException {

    public WhatsAppTransientException(String message) {
        super(message);
    }

    public WhatsAppTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
