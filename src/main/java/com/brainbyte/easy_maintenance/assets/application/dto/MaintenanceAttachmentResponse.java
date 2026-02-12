package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Resposta com os dados do anexo da manutenção")
public record MaintenanceAttachmentResponse(
        @Schema(description = "ID do anexo", example = "1")
        Long id,

        @Schema(description = "ID da manutenção", example = "100")
        Long maintenanceId,

        @Schema(description = "Tipo do anexo", example = "PHOTO")
        AttachmentType attachmentType,

        @Schema(description = "URL do arquivo", example = "https://s3.amazonaws.com/bucket/file.jpg")
        String fileUrl,

        @Schema(description = "Nome do arquivo", example = "foto_manutencao.jpg")
        String fileName,

        @Schema(description = "Tipo de conteúdo (MIME)", example = "image/jpeg")
        String contentType,

        @Schema(description = "Tamanho em bytes", example = "1048576")
        Long sizeBytes,

        @Schema(description = "ID do usuário que realizou o upload", example = "5")
        Long uploadedByUserId,

        @Schema(description = "Data/hora do upload", example = "2024-05-20T10:00:00Z")
        Instant uploadedAt
) {}
