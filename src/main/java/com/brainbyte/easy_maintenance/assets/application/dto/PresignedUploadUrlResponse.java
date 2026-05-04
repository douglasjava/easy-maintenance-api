package com.brainbyte.easy_maintenance.assets.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "URL pré-assinada para upload direto ao S3")
public record PresignedUploadUrlResponse(

        @Schema(description = "URL pré-assinada para PUT direto ao S3 (válida por 15 minutos)")
        String uploadUrl,

        @Schema(description = "Chave S3 do objeto — use no endpoint de confirmação após o upload")
        String s3Key,

        @Schema(description = "Momento de expiração da URL pré-assinada")
        Instant expiresAt
) {}
