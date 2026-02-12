package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentResponse;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceAttachmentService;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/maintenances")
@Tag(name = "Anexos", description = "Endpoints para gestão de anexos de manutenções")
public class MaintenanceAttachmentsController {

    private final MaintenanceAttachmentService service;

    @PostMapping(value = "/{maintenanceId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireTenant
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Realiza o upload de um anexo para uma manutenção",
            description = "Envia um arquivo (foto, laudo, nota fiscal, etc) associado a uma manutenção registrada.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Anexo enviado com sucesso"),
                    @ApiResponse(responseCode = "400", description = "Arquivo inválido ou tipo não suportado"),
                    @ApiResponse(responseCode = "404", description = "Manutenção não encontrada")
            }
    )
    public MaintenanceAttachmentResponse upload(
            @Parameter(description = "ID da manutenção") @PathVariable Long maintenanceId,
            @Parameter(description = "Tipo do anexo") @RequestParam AttachmentType type,
            @RequestPart("file") MultipartFile file) {
        return service.upload(maintenanceId, type, file);
    }

    @GetMapping("/{maintenanceId}/attachments")
    @RequireTenant
    @Operation(
            summary = "Lista os anexos de uma manutenção",
            description = "Retorna todos os metadados dos arquivos anexados a uma manutenção específica."
    )
    public List<MaintenanceAttachmentResponse> list(@PathVariable Long maintenanceId) {
        return service.listByMaintenance(maintenanceId);
    }

    @GetMapping("/attachments/{attachmentId}/download")
    @RequireTenant
    @Operation(
            summary = "Realiza o download de um anexo",
            description = "Retorna o fluxo de bytes do arquivo armazenado no S3."
    )
    public ResponseEntity<Resource> download(@PathVariable Long attachmentId) {
        InputStreamResource resource = new InputStreamResource(service.download(attachmentId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @RequireTenant
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Remove um anexo",
            description = "Exclui o registro do anexo e o arquivo físico no S3."
    )
    public void delete(@PathVariable Long attachmentId) {
        service.delete(attachmentId);
    }
}
