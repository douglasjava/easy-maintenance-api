package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.ConfirmUploadRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceAttachmentResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.PresignedUploadUrlRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.PresignedUploadUrlResponse;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.mapper.IMaintenanceAttachmentMapper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.S3Exception;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.storage.S3FileStorageService;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenanceAttachmentService {

    private final MaintenanceAttachmentRepository repository;
    private final S3FileStorageService fileStorageService;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;
    private final BillingSubscriptionItemRepository subscriptionItemRepository;
    private final BillingPlanFeaturesHelper featuresHelper;

    @Value("${aws.s3.upload.max-file-size-mb:50}")
    private long hardMaxFileSizeMb;

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
                    .uploadedByUserId(authenticationService.getCurrentUser().getId())
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

    public PresignedUploadUrlResponse generatePresignedUploadUrl(Long maintenanceId, PresignedUploadUrlRequest request) {
        String orgCode = TenantContext.get().orElseThrow(() -> new RuleException("Contexto de organização não encontrado"));
        BillingPlanFeatures features = getOrgBillingFeatures(orgCode);

        // Per-file size: use plan limit, bounded by hard config ceiling
        long planMaxFileSizeMb = Math.min(features.getMaxFileSizeMb(), hardMaxFileSizeMb);
        long maxFileBytes = planMaxFileSizeMb * 1024L * 1024L;
        if (request.sizeBytes() > maxFileBytes) {
            throw new RuleException(
                    String.format("Arquivo excede o tamanho máximo permitido de %d MB no plano atual", planMaxFileSizeMb));
        }

        // Monthly org upload quota
        long maxMonthlyBytes = (long) features.getMaxMonthlyUploadsMb() * 1024L * 1024L;
        Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long usedBytes = repository.sumSizeBytesByOrgSince(orgCode, startOfMonth);
        if (usedBytes + request.sizeBytes() > maxMonthlyBytes) {
            long usedMb = usedBytes / (1024L * 1024L);
            throw new RuleException(
                    String.format("Cota mensal de upload atingida. Utilizado: %d MB de %d MB.",
                            usedMb, features.getMaxMonthlyUploadsMb()));
        }

        String safeFileName = request.fileName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String s3Key = String.format("maintenances/%d/%s/%s", maintenanceId, UUID.randomUUID(), safeFileName);

        String uploadUrl = fileStorageService.generatePresignedPutUrl(s3Key, request.contentType());
        log.info("Presigned URL generated for maintenance {} — key: {}", maintenanceId, s3Key);

        return new PresignedUploadUrlResponse(uploadUrl, s3Key, Instant.now().plusSeconds(900));
    }

    private BillingPlanFeatures getOrgBillingFeatures(String orgCode) {
        var items = subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                BillingSubscriptionItemSourceType.ORGANIZATION, List.of(orgCode));
        if (items.isEmpty()) {
            log.warn("[UploadQuota] Nenhuma assinatura encontrada para org {}. Usando defaults.", orgCode);
            return new BillingPlanFeatures();
        }
        return featuresHelper.parse(items.getFirst().getPlan());
    }

    @Transactional
    public MaintenanceAttachmentResponse confirmUpload(Long maintenanceId, ConfirmUploadRequest request) {
        String expectedPrefix = "maintenances/" + maintenanceId + "/";
        if (!request.s3Key().startsWith(expectedPrefix)) {
            throw new RuleException("Chave S3 inválida para esta manutenção");
        }

        long maxBytes = hardMaxFileSizeMb * 1024L * 1024L;
        if (request.sizeBytes() > maxBytes) {
            throw new RuleException(
                    String.format("Arquivo excede o tamanho máximo permitido de %d MB", hardMaxFileSizeMb));
        }

        String fileUrl = fileStorageService.buildFileUrl(request.s3Key());

        if (repository.existsByFileUrl(fileUrl)) {
            throw new RuleException("Este arquivo já foi confirmado anteriormente");
        }

        if (!fileStorageService.objectExists(request.s3Key())) {
            throw new RuleException("Arquivo não encontrado no S3. O upload não foi concluído.");
        }

        MaintenanceAttachment attachment = MaintenanceAttachment.builder()
                .maintenanceId(maintenanceId)
                .attachmentType(request.attachmentType())
                .fileUrl(fileUrl)
                .fileName(request.fileName())
                .contentType(request.contentType())
                .sizeBytes(request.sizeBytes())
                .uploadedAt(Instant.now())
                .uploadedByUserId(authenticationService.getCurrentUser().getId())
                .build();

        MaintenanceAttachment saved = repository.save(attachment);
        auditService.log("MAINTENANCE_ATTACHMENT", saved.getId().toString(), AuditAction.CREATE, saved);

        log.info("Attachment confirmed for maintenance {} — id: {}, key: {}", maintenanceId, saved.getId(), request.s3Key());
        return IMaintenanceAttachmentMapper.INSTANCE.toResponse(saved);
    }

}
