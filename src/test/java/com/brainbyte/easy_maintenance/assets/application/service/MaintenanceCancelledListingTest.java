package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceCancelledListingTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemService maintenanceItemService;
    @Mock MaintenanceAttachmentRepository attachmentRepository;
    @Mock ServiceBase serviceBase;
    @Mock AuthenticationService authenticationService;
    @Mock UserRepository userRepository;

    @InjectMocks MaintenanceService service;

    private static final String ORG = "ORG-LIST-CANCELLED";
    private static final Long ITEM_ID = 30L;

    private MaintenanceItem item(String orgCode) {
        return MaintenanceItem.builder().id(ITEM_ID).organizationCode(orgCode).itemType("Extintor").build();
    }

    @Test
    void findCancelledByItem_returnsCancelledMaintenancesWithReasonAuthorAndDate() {
        Instant cancelledAt = Instant.now();
        Maintenance cancelled = Maintenance.builder()
                .id(1L).itemId(ITEM_ID).performedAt(LocalDate.now().minusDays(10))
                .type(MaintenanceType.PREVENTIVA)
                .cancelledAt(cancelledAt).cancelledBy(99L).cancelReason("Item errado").build();

        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item(ORG));
        when(maintenanceRepository.findCancelledByItemId(ITEM_ID)).thenReturn(List.of(cancelled));
        User canceller = new User();
        canceller.setId(99L);
        canceller.setName("Maria Síndica");
        when(userRepository.findAllById(Set.of(99L))).thenReturn(List.of(canceller));

        List<MaintenanceResponse> result = service.findCancelledByItem(ORG, ITEM_ID);

        assertThat(result).hasSize(1);
        MaintenanceResponse response = result.get(0);
        assertThat(response.cancelled()).isTrue();
        assertThat(response.cancelReason()).isEqualTo("Item errado");
        assertThat(response.cancelledAt()).isEqualTo(cancelledAt);
        assertThat(response.cancelledBy()).isEqualTo(99L);
        assertThat(response.cancelledByName()).isEqualTo("Maria Síndica");
        assertThat(response.itemType()).isEqualTo("Extintor");
    }

    @Test
    void findCancelledByItem_returnsEmptyList_whenNoneCancelled() {
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item(ORG));
        when(maintenanceRepository.findCancelledByItemId(ITEM_ID)).thenReturn(List.of());

        List<MaintenanceResponse> result = service.findCancelledByItem(ORG, ITEM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void findCancelledByItem_fallsBackToDash_whenCancellerNameNotResolvable() {
        Maintenance cancelled = Maintenance.builder()
                .id(1L).itemId(ITEM_ID).performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA)
                .cancelledAt(Instant.now()).cancelledBy(404L).cancelReason("Data errada").build();

        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item(ORG));
        when(maintenanceRepository.findCancelledByItemId(ITEM_ID)).thenReturn(List.of(cancelled));
        when(userRepository.findAllById(Set.of(404L))).thenReturn(List.of()); // usuário removido/não encontrado

        MaintenanceResponse response = service.findCancelledByItem(ORG, ITEM_ID).get(0);

        assertThat(response.cancelledByName()).isEqualTo("—");
    }

    @Test
    void findCancelledByItem_throwsTenantException_whenItemBelongsToAnotherOrg() {
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item("OUTRA-ORG"));

        assertThatThrownBy(() -> service.findCancelledByItem(ORG, ITEM_ID))
                .isInstanceOf(TenantException.class);
    }

    @Test
    void findById_and_listByItem_stillReportNotCancelled_forRegularMaintenance() {
        // Regressão: manutenções válidas continuam com cancelled=false e campos de cancelamento nulos
        // depois da adição dos novos campos ao DTO (TASK-139).
        Maintenance valid = Maintenance.builder()
                .id(2L).itemId(ITEM_ID).performedAt(LocalDate.now()).type(MaintenanceType.PREVENTIVA).build();

        when(maintenanceRepository.findById(2L)).thenReturn(java.util.Optional.of(valid));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item(ORG));
        when(attachmentRepository.findByMaintenanceId(2L)).thenReturn(List.of());

        MaintenanceResponse response = service.findById(ORG, 2L);

        assertThat(response.cancelled()).isFalse();
        assertThat(response.cancelReason()).isNull();
        assertThat(response.cancelledAt()).isNull();
        assertThat(response.cancelledBy()).isNull();
    }
}
