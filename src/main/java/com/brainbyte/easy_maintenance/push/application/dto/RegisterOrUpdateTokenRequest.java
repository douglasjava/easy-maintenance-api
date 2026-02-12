package com.brainbyte.easy_maintenance.push.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Requisição para registrar ou atualizar um token de push (público)")
public record RegisterOrUpdateTokenRequest(
        @Schema(description = "Token gerado pelo FCM", example = "fcm_token_123...")
        @NotBlank @Size(max = 512) String token,

        @Schema(description = "Plataforma do dispositivo", example = "WEB", defaultValue = "WEB")
        @Size(max = 20) String platform,

        @Schema(description = "Endpoint do serviço de push (opcional)", example = "https://fcm.googleapis.com/fcm/send")
        @Size(max = 600) String endpoint,

        @Schema(description = "Informações resumidas do dispositivo", example = "Chrome 121 / Windows 11")
        @Size(max = 255) String device_info
) {}
