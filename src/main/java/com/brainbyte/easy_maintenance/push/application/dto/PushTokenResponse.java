package com.brainbyte.easy_maintenance.push.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta com dados básicos do token registrado")
public record PushTokenResponse(
        @Schema(description = "ID único do registro", example = "1")
        Long id,

        @Schema(description = "Token registrado", example = "fcm_token_123...")
        String token,

        @Schema(description = "Plataforma associada", example = "WEB")
        String platform,

        @Schema(description = "Status de ativação", example = "true")
        boolean is_active
) {}
