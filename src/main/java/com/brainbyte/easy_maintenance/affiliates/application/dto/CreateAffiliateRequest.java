package com.brainbyte.easy_maintenance.affiliates.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateAffiliateRequest(
        @Schema(description = "Nome completo do afiliado", example = "João Silva")
        @NotBlank String name,

        @Schema(description = "E-mail do afiliado", example = "joao@email.com")
        @NotBlank @Email String email,

        @Schema(description = "WhatsApp do afiliado", example = "31999999999")
        @NotBlank String whatsapp
) {}
