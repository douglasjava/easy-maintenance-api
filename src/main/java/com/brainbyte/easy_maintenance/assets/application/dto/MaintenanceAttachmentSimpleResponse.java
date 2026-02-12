package com.brainbyte.easy_maintenance.assets.application.dto;

import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados simplificados do anexo da manutenção")
public record MaintenanceAttachmentSimpleResponse(
        @Schema(description = "ID do anexo", example = "1")
        Long id,

        @Schema(description = "Nome do arquivo", example = "nota_fiscal.pdf")
        String fileName,

        @Schema(description = "Tipo do anexo", example = "REPORT")
        AttachmentType attachmentType
) {}
