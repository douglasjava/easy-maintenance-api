package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentResponse;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceAttachmentMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.S3Exception;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.storage.S3FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceAttachmentService {

    private final MaintenanceAttachmentRepository repository;
    private final S3FileStorageService fileStorageService;
    private final AuditService auditService;

    @Transactional
    public MaintenanceAttachmentResponse upload(Long maintenanceId, AttachmentType type, MultipartFile file) {
        log.info("Uploading attachment for maintenance {}: {}", maintenanceId, file.getOriginalFilename());

        try {
            String path = "maintenances/" + maintenanceId;
            String fileUrl = fileStorageService.upload(
                    path,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getInputStream(),
                    file.getSize()
            );

            MaintenanceAttachment attachment = MaintenanceAttachment.builder()
                    .maintenanceId(maintenanceId)
                    .attachmentType(type)
                    .fileUrl(fileUrl)
                    .fileName(file.getOriginalFilename())
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .uploadedAt(java.time.Instant.now())
                    .build();

            MaintenanceAttachment saved = repository.save(attachment);

            auditService.log("MAINTENANCE_ATTACHMENT", saved.getId().toString(), AuditAction.CREATE, saved);

            return IMaintenanceAttachmentMapper.INSTANCE.toResponse(saved);
        } catch (Exception e) {
            log.error("Error reading file stream", e);
            throw new S3Exception("Failed to upload file", e);
        }
    }

    public List<MaintenanceAttachmentResponse> listByMaintenance(Long maintenanceId) {
        return repository.findByMaintenanceId(maintenanceId).stream()
                .map(IMaintenanceAttachmentMapper.INSTANCE::toResponse)
                .collect(Collectors.toList());
    }

    public InputStream download(Long attachmentId) {
        MaintenanceAttachment attachment = repository.findById(attachmentId)
                .orElseThrow(() -> new S3Exception(String.format("Attachment not found: %s", attachmentId)));
        return fileStorageService.download(attachment.getFileUrl());
    }

    @Transactional
    public void delete(Long attachmentId) {
        MaintenanceAttachment attachment = repository.findById(attachmentId)
                .orElseThrow(() -> new S3Exception(String.format("Attachment not found: %s", attachmentId)));

        fileStorageService.delete(attachment.getFileUrl());
        repository.delete(attachment);

        auditService.log("MAINTENANCE_ATTACHMENT", attachmentId.toString(), AuditAction.DELETE, attachment);
    }

}
