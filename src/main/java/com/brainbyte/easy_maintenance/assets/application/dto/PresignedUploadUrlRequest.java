package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Requisição para obter uma URL pré-assinada para upload direto ao S3")
public record PresignedUploadUrlRequest(

        @Schema(description = "Nome original do arquivo", example = "laudo_tecnico.pdf")
        @NotBlank
        String fileName,

        @Schema(description = "Tipo MIME do arquivo", example = "application/pdf")
        @NotBlank
        String contentType,

        @Schema(description = "Tipo do anexo", example = "REPORT")
        @NotNull
        AttachmentType attachmentType,

        @Schema(description = "Tamanho do arquivo em bytes", example = "1048576")
        @Positive
        long sizeBytes
) {}
