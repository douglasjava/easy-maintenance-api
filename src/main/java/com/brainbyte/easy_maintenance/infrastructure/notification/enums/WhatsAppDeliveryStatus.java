package com.brainbyte.easy_maintenance.infrastructure.notification.enums;

/**
 * Status de entrega reportado pela Meta via webhook (TASK-128) — diferente de
 * {@link BusinessWhatsAppDispatchStatus}, que descreve o resultado do envio outbound
 * (aceito/rejeitado pela nossa integração). Este enum reflete o ciclo de vida da mensagem
 * já aceita pela Graph API: SENT -> DELIVERED -> READ, ou SENT -> FAILED.
 */
public enum WhatsAppDeliveryStatus {
    SENT,
    DELIVERED,
    READ,
    FAILED
}
