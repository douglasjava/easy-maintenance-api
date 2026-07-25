package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Resposta de sucesso da Graph API ao enviar uma mensagem (contém o wamid). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppMessageResponse(
        @JsonProperty("messaging_product") String messagingProduct,
        List<MessageId> messages
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageId(String id) {
    }
}
