package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.ForbiddenException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.TenantException;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceCancelTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemService maintenanceItemService;
    @Mock MaintenanceAttachmentRepository attachmentRepository;
    @Mock ServiceBase serviceBase;
    @Mock AuthenticationService authenticationService;

    @InjectMocks MaintenanceService service;

    private static final String ORG = "ORG-CANCEL";
    private static final Long MAINTENANCE_ID = 100L;
    private static final Long ITEM_ID = 10L;
    private static final Long USER_ID = 42L;
    private static final String REASON = "Item errado — deveria ser no extintor da cozinha";

    private User user(Role role) {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(role);
        return user;
    }

    private Maintenance maintenance() {
        return Maintenance.builder()
                .id(MAINTENANCE_ID)
                .itemId(ITEM_ID)
                .performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA)
                .build();
    }

    private MaintenanceItem item(String orgCode) {
        return MaintenanceItem.builder().id(ITEM_ID).organizationCode(orgCode).build();
    }

    // ── happy path ──────────────────────────────────────────────────────────

    @Test
    void cancel_persistsReasonAuthorAndTimestamp_thenSoftDeletes() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item(ORG));

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        ArgumentCaptor<Maintenance> captor = ArgumentCaptor.forClass(Maintenance.class);
        verify(maintenanceRepository).saveAndFlush(captor.capture());
        Maintenance saved = captor.getValue();
        assertThat(saved.getCancelReason()).isEqualTo(REASON);
        assertThat(saved.getCancelledBy()).isEqualTo(USER_ID);
        assertThat(saved.getCancelledAt()).isNotNull();
        assertThat(saved.getActiveDedupKey())
                .as("libera (item_id, performed_at) pra uma nova manutenção no mesmo dia — ver V85")
                .isEqualTo(MAINTENANCE_ID);

        verify(maintenanceRepository).delete(saved);
    }

    @Test
    void cancel_allowsSyndicRole() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.SYNDIC));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item(ORG));

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        verify(maintenanceRepository).delete(any(Maintenance.class));
    }

    // ── permissão ───────────────────────────────────────────────────────────

    @Test
    void cancel_throwsForbidden_whenUserIsTech() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.TECH));

        assertThatThrownBy(() -> service.cancel(ORG, MAINTENANCE_ID, REASON))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(maintenanceRepository);
    }

    @Test
    void cancel_throwsForbidden_whenUserIsReader() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.READER));

        assertThatThrownBy(() -> service.cancel(ORG, MAINTENANCE_ID, REASON))
                .isInstanceOf(ForbiddenException.class);
    }

    // ── não encontrada vs já cancelada ──────────────────────────────────────

    @Test
    void cancel_throwsNotFound_whenMaintenanceNeverExisted() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.empty());
        when(maintenanceRepository.existsCancelledByIdAndOrgCode(MAINTENANCE_ID, ORG)).thenReturn(false);

        assertThatThrownBy(() -> service.cancel(ORG, MAINTENANCE_ID, REASON))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancel_throwsConflict_whenAlreadyCancelled() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.empty());
        when(maintenanceRepository.existsCancelledByIdAndOrgCode(MAINTENANCE_ID, ORG)).thenReturn(true);

        assertThatThrownBy(() -> service.cancel(ORG, MAINTENANCE_ID, REASON))
                .isInstanceOf(ConflictException.class);

        verify(maintenanceRepository, never()).saveAndFlush(any());
        verify(maintenanceRepository, never()).delete(any(Maintenance.class));
    }

    // ── isolamento multi-tenant ─────────────────────────────────────────────

    @Test
    void cancel_throwsTenantException_whenMaintenanceBelongsToAnotherOrg() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item("OUTRA-ORG"));

        assertThatThrownBy(() -> service.cancel(ORG, MAINTENANCE_ID, REASON))
                .isInstanceOf(TenantException.class);

        verify(maintenanceRepository, never()).saveAndFlush(any());
    }

    // ── TASK-138: recálculo do item após cancelamento ──────────────────────

    @Test
    void cancel_whenNoValidMaintenanceRemains_resetsItemToNeverPerformedState() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        MaintenanceItem item = item(ORG);
        item.setLastPerformedAt(LocalDate.now());
        item.setNextDueAt(LocalDate.now().plusMonths(6));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        when(maintenanceRepository.findFirstByItemIdOrderByPerformedAtDescIdDesc(ITEM_ID))
                .thenReturn(Optional.empty());
        when(serviceBase.resolvePeriod(item)).thenReturn(Period.ofMonths(6));

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        verify(maintenanceItemService).save(captor.capture());
        MaintenanceItem saved = captor.getValue();
        assertThat(saved.getLastPerformedAt()).isNull();
        assertThat(saved.getNextDueAt()).isEqualTo(LocalDate.now().plusMonths(6));
    }

    @Test
    void cancel_whenNoValidMaintenanceRemainsAndItemHasNoPeriod_clearsNextDueAt() {
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        MaintenanceItem item = item(ORG);
        item.setNextDueAt(LocalDate.now().plusMonths(6));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        when(maintenanceRepository.findFirstByItemIdOrderByPerformedAtDescIdDesc(ITEM_ID))
                .thenReturn(Optional.empty());
        when(serviceBase.resolvePeriod(item)).thenReturn(null);

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        verify(maintenanceItemService).save(captor.capture());
        assertThat(captor.getValue().getNextDueAt()).isNull();
    }

    @Test
    void cancel_theMostRecentMaintenance_recalculatesFromThePreviousValidOne() {
        LocalDate previousPerformedAt = LocalDate.now().minusMonths(3);
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        MaintenanceItem item = item(ORG);
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        Maintenance previous = Maintenance.builder().id(1L).itemId(ITEM_ID).performedAt(previousPerformedAt).build();
        when(maintenanceRepository.findFirstByItemIdOrderByPerformedAtDescIdDesc(ITEM_ID))
                .thenReturn(Optional.of(previous));
        when(serviceBase.resolvePeriod(item)).thenReturn(Period.ofMonths(6));

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        verify(maintenanceItemService).save(captor.capture());
        MaintenanceItem saved = captor.getValue();
        assertThat(saved.getLastPerformedAt()).isEqualTo(previousPerformedAt);
        assertThat(saved.getNextDueAt()).isEqualTo(previousPerformedAt.plusMonths(6));
    }

    @Test
    void cancel_middleMaintenance_recalculatesFromTheMostRecentValidOne_notThePreviousOne() {
        // Cenário M1/M2/M3: cancelar M2 (do meio) deve recalcular a partir de M3 (mais recente
        // válida por performedAt), não de M1 (a anterior a M2) — a query já ordena por
        // performedAt DESC, então o repositório mockado retornando M3 cobre exatamente isso.
        LocalDate m3PerformedAt = LocalDate.now().minusDays(1); // M3: mais recente válida
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        MaintenanceItem item = item(ORG);
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        Maintenance m3 = Maintenance.builder().id(3L).itemId(ITEM_ID).performedAt(m3PerformedAt).build();
        when(maintenanceRepository.findFirstByItemIdOrderByPerformedAtDescIdDesc(ITEM_ID))
                .thenReturn(Optional.of(m3));
        when(serviceBase.resolvePeriod(item)).thenReturn(Period.ofMonths(6));

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        verify(maintenanceItemService).save(captor.capture());
        MaintenanceItem saved = captor.getValue();
        assertThat(saved.getLastPerformedAt()).isEqualTo(m3PerformedAt);
        assertThat(saved.getNextDueAt()).isEqualTo(m3PerformedAt.plusMonths(6));
    }

    @Test
    void cancel_maintenanceThatDoesNotAffectCurrentState_stillRecalculatesFromCurrentMostRecentValid() {
        // Cancelar M1 quando M3 já é a válida mais recente: o resultado não muda em relação ao
        // estado atual, mas o método sempre recalcula a partir da consulta real — não assume que
        // "não mudou nada" com base em qual manutenção foi cancelada.
        LocalDate m3PerformedAt = LocalDate.now().minusDays(1);
        when(authenticationService.getCurrentUser()).thenReturn(user(Role.ADMIN));
        when(maintenanceRepository.findById(MAINTENANCE_ID)).thenReturn(Optional.of(maintenance()));
        MaintenanceItem item = item(ORG);
        item.setLastPerformedAt(m3PerformedAt);
        item.setNextDueAt(m3PerformedAt.plusMonths(6));
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        Maintenance m3 = Maintenance.builder().id(3L).itemId(ITEM_ID).performedAt(m3PerformedAt).build();
        when(maintenanceRepository.findFirstByItemIdOrderByPerformedAtDescIdDesc(ITEM_ID))
                .thenReturn(Optional.of(m3));
        when(serviceBase.resolvePeriod(item)).thenReturn(Period.ofMonths(6));

        service.cancel(ORG, MAINTENANCE_ID, REASON);

        ArgumentCaptor<MaintenanceItem> captor = ArgumentCaptor.forClass(MaintenanceItem.class);
        verify(maintenanceItemService).save(captor.capture());
        MaintenanceItem saved = captor.getValue();
        assertThat(saved.getLastPerformedAt()).isEqualTo(m3PerformedAt);
        assertThat(saved.getNextDueAt()).isEqualTo(m3PerformedAt.plusMonths(6));
    }
}
