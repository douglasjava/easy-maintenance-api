package com.brainbyte.easy_maintenance.push.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Requisição para desativar um token de push")
public record DisableTokenRequest(
        @Schema(description = "Token a ser desativado", example = "fcm_token_123...")
        @NotBlank @Size(max = 512) String token
) {}
