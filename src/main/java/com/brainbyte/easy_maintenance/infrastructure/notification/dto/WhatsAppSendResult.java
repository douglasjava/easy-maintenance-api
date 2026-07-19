package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

/** Resultado de um envio bem-sucedido — o wamid é usado pela TASK-130 para persistir o dispatch
 * e pela TASK-128 (webhook) para atualizar o status de entrega/leitura depois. */
public record WhatsAppSendResult(String wamid) {
}
