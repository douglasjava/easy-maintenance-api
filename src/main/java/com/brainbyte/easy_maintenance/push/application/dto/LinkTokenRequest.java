package com.brainbyte.easy_maintenance.push.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Requisição para vincular um token ao usuário autenticado")
public record LinkTokenRequest(
        @Schema(description = "Token gerado pelo FCM para ser vinculado", example = "fcm_token_123...")
        @NotBlank @Size(max = 512) String token
) {}
