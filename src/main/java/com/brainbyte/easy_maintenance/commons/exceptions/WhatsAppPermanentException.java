package com.brainbyte.easy_maintenance.commons.exceptions;

/**
 * Falha permanente no envio de WhatsApp (template/número inválido, erro 130497 — restrição de
 * país, token expirado/inválido) — NÃO deve ter retry (ver resilience4j.retry.instances.whatsapp
 * ignore-exceptions em application.properties). Retentar não resolveria a causa raiz e só geraria
 * custo extra por conversa.
 */
public class WhatsAppPermanentException extends RuntimeException {

    public WhatsAppPermanentException(String message) {
        super(message);
    }

    public WhatsAppPermanentException(String message, Throwable cause) {
        super(message, cause);
    }
}
