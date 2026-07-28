package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.AttachmentType;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * TASK-142: "anexado por {nome} em {data}" — o dado (uploadedByUserId/uploadedAt) já existia em
 * MaintenanceAttachment desde sempre; o gap era só de exposição/resolução de nome. Mesmo padrão de
 * resolução em lote já usado para cancelledByName (TASK-139/141) e MaintenanceExportService
 * (TASK-104).
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceAttachmentAuthorTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemService maintenanceItemService;
    @Mock MaintenanceAttachmentRepository attachmentRepository;
    @Mock ServiceBase serviceBase;
    @Mock AuthenticationService authenticationService;
    @Mock UserRepository userRepository;

    @InjectMocks MaintenanceService service;

    private static final String ORG = "ORG-ATTACH-AUTHOR";
    private static final Long ITEM_ID = 40L;

    private MaintenanceItem item() {
        return MaintenanceItem.builder().id(ITEM_ID).organizationCode(ORG).itemType("Extintor").build();
    }

    private MaintenanceAttachment attachment(Long id, Long maintenanceId, Long uploadedBy, Instant uploadedAt) {
        return MaintenanceAttachment.builder()
                .id(id).maintenanceId(maintenanceId).fileName("nota.pdf").attachmentType(AttachmentType.REPORT)
                .uploadedByUserId(uploadedBy).uploadedAt(uploadedAt).build();
    }

    // ── findById: um anexo, um autor resolvido ──────────────────────────────

    @Test
    void findById_resolvesAttachmentAuthorNameAndUploadedAt() {
        Instant uploadedAt = Instant.now();
        Maintenance maintenance = Maintenance.builder().id(1L).itemId(ITEM_ID).performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA).build();
        when(maintenanceRepository.findById(1L)).thenReturn(Optional.of(maintenance));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item());
        when(attachmentRepository.findByMaintenanceId(1L))
                .thenReturn(List.of(attachment(10L, 1L, 55L, uploadedAt)));
        User uploader = new User();
        uploader.setId(55L);
        uploader.setName("Técnico João");
        when(userRepository.findAllById(Set.of(55L))).thenReturn(List.of(uploader));

        MaintenanceResponse response = service.findById(ORG, 1L);

        assertThat(response.attachments()).hasSize(1);
        var att = response.attachments().get(0);
        assertThat(att.uploadedByUserId()).isEqualTo(55L);
        assertThat(att.uploadedByName()).isEqualTo("Técnico João");
        assertThat(att.uploadedAt()).isEqualTo(uploadedAt);
    }

    @Test
    void findById_fallsBackToDash_whenUploaderNotResolvable() {
        Maintenance maintenance = Maintenance.builder().id(1L).itemId(ITEM_ID).performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA).build();
        when(maintenanceRepository.findById(1L)).thenReturn(Optional.of(maintenance));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item());
        when(attachmentRepository.findByMaintenanceId(1L))
                .thenReturn(List.of(attachment(10L, 1L, 404L, Instant.now())));
        when(userRepository.findAllById(Set.of(404L))).thenReturn(List.of());

        MaintenanceResponse response = service.findById(ORG, 1L);

        assertThat(response.attachments().get(0).uploadedByName()).isEqualTo("—");
    }

    @Test
    void findById_withNoAttachments_neverCallsUserRepository() {
        Maintenance maintenance = Maintenance.builder().id(1L).itemId(ITEM_ID).performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA).build();
        when(maintenanceRepository.findById(1L)).thenReturn(Optional.of(maintenance));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item());
        when(attachmentRepository.findByMaintenanceId(1L)).thenReturn(List.of());

        service.findById(ORG, 1L);

        verifyNoInteractions(userRepository);
    }

    // ── findCancelledByItem: anexos de múltiplas manutenções resolvidos numa única query ────

    @Test
    void findCancelledByItem_resolvesAttachmentAuthors_acrossMultipleMaintenances_withoutNPlusOne() {
        Maintenance m1 = Maintenance.builder().id(1L).itemId(ITEM_ID).performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA).cancelledAt(Instant.now()).cancelledBy(1L).build();
        Maintenance m2 = Maintenance.builder().id(2L).itemId(ITEM_ID).performedAt(LocalDate.now())
                .type(MaintenanceType.CORRETIVA).cancelledAt(Instant.now()).cancelledBy(1L).build();

        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item());
        when(maintenanceRepository.findCancelledByItemId(ITEM_ID)).thenReturn(List.of(m1, m2));
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of()); // canceller (fallback "—", não é o foco aqui)
        when(attachmentRepository.findByMaintenanceIdIn(Set.of(1L, 2L))).thenReturn(List.of(
                attachment(10L, 1L, 55L, Instant.now()),
                attachment(11L, 2L, 66L, Instant.now())
        ));
        User u55 = new User(); u55.setId(55L); u55.setName("Técnico João");
        User u66 = new User(); u66.setId(66L); u66.setName("Técnica Ana");
        when(userRepository.findAllById(Set.of(55L, 66L))).thenReturn(List.of(u55, u66));

        List<MaintenanceResponse> result = service.findCancelledByItem(ORG, ITEM_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).attachments().get(0).uploadedByName()).isEqualTo("Técnico João");
        assertThat(result.get(1).attachments().get(0).uploadedByName()).isEqualTo("Técnica Ana");
        // uma query pra buscar TODOS os anexos das duas manutenções, não uma por manutenção
        verify(attachmentRepository, times(1)).findByMaintenanceIdIn(anySet());
        verify(attachmentRepository, never()).findByMaintenanceId(anyLong());
    }
}
