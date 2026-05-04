package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.ConfirmUploadRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.PresignedUploadUrlRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.PresignedUploadUrlResponse;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.storage.S3FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceAttachmentServiceTest {

    @Mock
    private MaintenanceAttachmentRepository repository;

    @Mock
    private S3FileStorageService fileStorageService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private MaintenanceAttachmentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxFileSizeMb", 10L);
    }

    // --- generatePresignedUploadUrl ---

    @Test
    void shouldGeneratePresignedUploadUrl_whenFileSizeIsWithinLimit() {
        when(fileStorageService.generatePresignedPutUrl(anyString(), anyString()))
                .thenReturn("https://bucket.s3.amazonaws.com/presigned-url");

        PresignedUploadUrlRequest request = new PresignedUploadUrlRequest(
                "laudo.pdf", "application/pdf", AttachmentType.REPORT, 5 * 1024 * 1024L);

        PresignedUploadUrlResponse response = service.generatePresignedUploadUrl(1L, request);

        assertThat(response.uploadUrl()).isEqualTo("https://bucket.s3.amazonaws.com/presigned-url");
        assertThat(response.s3Key()).startsWith("maintenances/1/");
        assertThat(response.s3Key()).endsWith("laudo.pdf");
        assertThat(response.expiresAt()).isNotNull();
        verify(fileStorageService).generatePresignedPutUrl(anyString(), eq("application/pdf"));
    }

    @Test
    void shouldSanitizeFileNameInS3Key() {
        when(fileStorageService.generatePresignedPutUrl(anyString(), anyString()))
                .thenReturn("https://presigned");

        PresignedUploadUrlRequest request = new PresignedUploadUrlRequest(
                "arquivo com espaços!.pdf", "application/pdf", AttachmentType.OTHER, 1024L);

        PresignedUploadUrlResponse response = service.generatePresignedUploadUrl(1L, request);

        assertThat(response.s3Key()).doesNotContain(" ");
        assertThat(response.s3Key()).doesNotContain("!");
    }

    @Test
    void shouldThrowRuleException_whenFileSizeExceedsLimit() {
        PresignedUploadUrlRequest request = new PresignedUploadUrlRequest(
                "grande.zip", "application/zip", AttachmentType.OTHER, 11 * 1024 * 1024L);

        assertThatThrownBy(() -> service.generatePresignedUploadUrl(1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("10 MB");

        verifyNoInteractions(fileStorageService);
    }

    // --- confirmUpload ---

    @Test
    void shouldConfirmUpload_andPersistAttachment() {
        String s3Key = "maintenances/42/some-uuid/foto.jpg";
        when(fileStorageService.buildFileUrl(s3Key))
                .thenReturn("https://bucket.s3.amazonaws.com/" + s3Key);

        MaintenanceAttachment saved = MaintenanceAttachment.builder()
                .id(10L)
                .maintenanceId(42L)
                .attachmentType(AttachmentType.PHOTO)
                .fileUrl("https://bucket.s3.amazonaws.com/" + s3Key)
                .fileName("foto.jpg")
                .contentType("image/jpeg")
                .sizeBytes(204800L)
                .build();
        when(repository.save(any())).thenReturn(saved);

        ConfirmUploadRequest request = new ConfirmUploadRequest(
                s3Key, "foto.jpg", "image/jpeg", 204800L, AttachmentType.PHOTO);

        var response = service.confirmUpload(42L, request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.fileUrl()).contains(s3Key);
        verify(repository).save(any());
        verify(auditService).log(eq("MAINTENANCE_ATTACHMENT"), eq("10"), any(), any());
    }

    @Test
    void shouldThrowRuleException_whenS3KeyBelongsToDifferentMaintenance() {
        ConfirmUploadRequest request = new ConfirmUploadRequest(
                "maintenances/999/uuid/arquivo.pdf", "arquivo.pdf", "application/pdf",
                1024L, AttachmentType.REPORT);

        assertThatThrownBy(() -> service.confirmUpload(42L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("inválida");

        verifyNoInteractions(repository, fileStorageService, auditService);
    }

    @Test
    void shouldThrowRuleException_onConfirm_whenFileSizeExceedsLimit() {
        ConfirmUploadRequest request = new ConfirmUploadRequest(
                "maintenances/1/uuid/grande.zip", "grande.zip", "application/zip",
                20 * 1024 * 1024L, AttachmentType.OTHER);

        assertThatThrownBy(() -> service.confirmUpload(1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("10 MB");

        verifyNoInteractions(repository, auditService);
    }
}
