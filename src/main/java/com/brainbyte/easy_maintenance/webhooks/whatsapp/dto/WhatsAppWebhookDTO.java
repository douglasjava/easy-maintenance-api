package com.brainbyte.easy_maintenance.webhooks.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Mirror do payload de webhook da WhatsApp Cloud API (Meta), formato
 * {@code entry[].changes[].value.statuses[]} / {@code entry[].changes[].value.messages[]}.
 * Records aninhados no mesmo padrão de {@code AsaasDTO}, todos com
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} pois a Meta pode adicionar campos novos
 * ao payload sem aviso prévio.
 */
public class WhatsAppWebhookDTO {

    private WhatsAppWebhookDTO() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookEventPayload(
            String object,
            List<Entry> entry
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String id,
            List<Change> changes
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
            Value value,
            String field
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(
            @JsonProperty("messaging_product") String messagingProduct,
            List<StatusEntry> statuses,
            List<InboundMessage> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusEntry(
            String id, // wamid
            String status, // sent | delivered | read | failed
            String timestamp, // epoch seconds, string
            @JsonProperty("recipient_id") String recipientId,
            List<StatusError> errors
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusError(
            Integer code,
            String title,
            String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InboundMessage(
            String id, // wamid da mensagem inbound
            String from,
            String timestamp,
            String type
    ) {
    }
}
