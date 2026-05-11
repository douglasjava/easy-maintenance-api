package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.ConfirmUploadRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.PresignedUploadUrlRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.PresignedUploadUrlResponse;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlanFeatures;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.storage.S3FileStorageService;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MaintenanceAttachmentServiceTest {

    @Mock
    private MaintenanceAttachmentRepository repository;

    @Mock
    private S3FileStorageService fileStorageService;

    @Mock
    private AuditService auditService;

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private BillingSubscriptionItemRepository subscriptionItemRepository;

    @Mock
    private BillingPlanFeaturesHelper featuresHelper;

    @InjectMocks
    private MaintenanceAttachmentService service;

    private static final String ORG_CODE = "org-A";
    private static final BillingSubscriptionItem STUB_ITEM = mock(BillingSubscriptionItem.class);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "hardMaxFileSizeMb", 50L);
        TenantContext.set(ORG_CODE);

        BillingPlanFeatures defaultFeatures = BillingPlanFeatures.builder()
                .maxFileSizeMb(20)
                .maxMonthlyUploadsMb(2048)
                .build();
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), any()))
                .thenReturn(List.of(STUB_ITEM));
        when(featuresHelper.parse(any())).thenReturn(defaultFeatures);
        when(repository.sumSizeBytesByOrgSince(eq(ORG_CODE), any(Instant.class))).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
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
    void shouldThrowRuleException_whenFileSizeExceedsPlanLimit() {
        // Plan allows 20 MB max
        PresignedUploadUrlRequest request = new PresignedUploadUrlRequest(
                "grande.zip", "application/zip", AttachmentType.OTHER, 25 * 1024 * 1024L);

        assertThatThrownBy(() -> service.generatePresignedUploadUrl(1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("20 MB");

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void shouldThrowRuleException_whenMonthlyQuotaWouldBeExceeded() {
        // Already used 2040 MB of 2048 MB quota
        when(repository.sumSizeBytesByOrgSince(eq(ORG_CODE), any(Instant.class)))
                .thenReturn(2040L * 1024L * 1024L);

        // Request 10 MB (within per-file limit of 20 MB) — would push total to 2050 MB > 2048 MB
        PresignedUploadUrlRequest request = new PresignedUploadUrlRequest(
                "file.pdf", "application/pdf", AttachmentType.OTHER, 10 * 1024 * 1024L);

        assertThatThrownBy(() -> service.generatePresignedUploadUrl(1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Cota mensal");

        verifyNoInteractions(fileStorageService);
    }

    @Test
    void shouldThrowRuleException_whenNoTenantContext() {
        TenantContext.clear();

        PresignedUploadUrlRequest request = new PresignedUploadUrlRequest(
                "arquivo.pdf", "application/pdf", AttachmentType.REPORT, 1024L);

        assertThatThrownBy(() -> service.generatePresignedUploadUrl(1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("organização");
    }

    // --- confirmUpload ---

    @Test
    void shouldConfirmUpload_andPersistAttachment() {
        String s3Key = "maintenances/42/some-uuid/foto.jpg";
        String fileUrl = "https://bucket.s3.us-east-1.amazonaws.com/" + s3Key;

        when(fileStorageService.buildFileUrl(s3Key)).thenReturn(fileUrl);
        when(repository.existsByFileUrl(fileUrl)).thenReturn(false);
        when(fileStorageService.objectExists(s3Key)).thenReturn(true);

        User user = new User();
        user.setId(99L);
        when(authenticationService.getCurrentUser()).thenReturn(user);

        MaintenanceAttachment saved = MaintenanceAttachment.builder()
                .id(10L)
                .maintenanceId(42L)
                .attachmentType(AttachmentType.PHOTO)
                .fileUrl(fileUrl)
                .fileName("foto.jpg")
                .contentType("image/jpeg")
                .sizeBytes(204800L)
                .uploadedByUserId(99L)
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
    void shouldThrowRuleException_whenS3KeyAlreadyConfirmed() {
        String s3Key = "maintenances/42/some-uuid/foto.jpg";
        String fileUrl = "https://bucket.s3.us-east-1.amazonaws.com/" + s3Key;

        when(fileStorageService.buildFileUrl(s3Key)).thenReturn(fileUrl);
        when(repository.existsByFileUrl(fileUrl)).thenReturn(true);

        ConfirmUploadRequest request = new ConfirmUploadRequest(
                s3Key, "foto.jpg", "image/jpeg", 204800L, AttachmentType.PHOTO);

        assertThatThrownBy(() -> service.confirmUpload(42L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("já foi confirmado");

        verify(fileStorageService, never()).objectExists(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldThrowRuleException_whenFileNotYetUploadedToS3() {
        String s3Key = "maintenances/42/some-uuid/foto.jpg";
        String fileUrl = "https://bucket.s3.us-east-1.amazonaws.com/" + s3Key;

        when(fileStorageService.buildFileUrl(s3Key)).thenReturn(fileUrl);
        when(repository.existsByFileUrl(fileUrl)).thenReturn(false);
        when(fileStorageService.objectExists(s3Key)).thenReturn(false);

        ConfirmUploadRequest request = new ConfirmUploadRequest(
                s3Key, "foto.jpg", "image/jpeg", 204800L, AttachmentType.PHOTO);

        assertThatThrownBy(() -> service.confirmUpload(42L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("não foi concluído");

        verifyNoInteractions(auditService);
        verify(repository, never()).save(any());
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
    void shouldThrowRuleException_onConfirm_whenFileSizeExceedsHardLimit() {
        ConfirmUploadRequest request = new ConfirmUploadRequest(
                "maintenances/1/uuid/grande.zip", "grande.zip", "application/zip",
                60 * 1024 * 1024L, AttachmentType.OTHER);

        assertThatThrownBy(() -> service.confirmUpload(1L, request))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("50 MB");

        verifyNoInteractions(repository, auditService);
    }
}
