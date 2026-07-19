package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Corpo da requisição de envio de template da Graph API da Meta
 * (POST /{phone-number-id}/messages). Ver
 * https://developers.facebook.com/docs/whatsapp/cloud-api/reference/messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WhatsAppTemplateMessageRequest {

    @JsonProperty("messaging_product")
    private String messagingProduct;
    private String to;
    private String type;
    private Template template;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Template {
        private String name;
        private Language language;
        private List<Component> components;
    }

    public record Language(String code) {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Component {
        private String type;
        private List<Parameter> parameters;
    }

    public record Parameter(String type, String text) {
    }

}
