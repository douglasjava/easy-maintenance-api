package com.brainbyte.easy_maintenance.infrastructure.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Corpo de erro da Graph API — usado para extrair o `error.code` e classificar a falha. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppErrorResponse(ErrorDetail error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(
            String message,
            String type,
            Integer code,
            @JsonProperty("error_subcode") Integer errorSubcode,
            @JsonProperty("fbtrace_id") String fbtraceId
    ) {
    }
}
