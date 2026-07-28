package com.brainbyte.easy_maintenance.assets.application.service;

import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.component.ServiceBase;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceAttachmentRepository;
import com.brainbyte.easy_maintenance.assets.infrastructure.persistence.MaintenanceRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Regressão da extração feita na TASK-138: register() passou a chamar
 * MaintenanceService#applyPerformedMaintenance (compartilhado com o recálculo do cancelamento) em
 * vez de calcular nextDueAt inline. Estes testes travam o comportamento pré-existente,
 * especialmente o caso "period == null" (nextDueAt fica como está, não vira null).
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceRegisterCalculationTest {

    @Mock MaintenanceRepository maintenanceRepository;
    @Mock MaintenanceItemService maintenanceItemService;
    @Mock MaintenanceAttachmentRepository attachmentRepository;
    @Mock ServiceBase serviceBase;
    @Mock AuthenticationService authenticationService;

    @InjectMocks MaintenanceService service;

    private static final String ORG = "ORG-REGISTER";
    private static final Long ITEM_ID = 20L;
    private static final Long USER_ID = 7L;

    private RegisterMaintenanceRequest request(LocalDate performedAt) {
        return new RegisterMaintenanceRequest(performedAt, MaintenanceType.PREVENTIVA, "Técnico", 1000, null);
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        return user;
    }

    @Test
    void register_withPeriodConfigured_setsNextDueAtFromPerformedAtPlusPeriod() {
        LocalDate performedAt = LocalDate.now();
        MaintenanceItem item = MaintenanceItem.builder().id(ITEM_ID).organizationCode(ORG).build();
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        when(maintenanceRepository.existsByItemIdAndPerformedAt(ITEM_ID, LocalDate.now())).thenReturn(false);
        when(authenticationService.getCurrentUser()).thenReturn(user());
        when(serviceBase.resolvePeriod(item)).thenReturn(Period.ofMonths(6));
        when(maintenanceItemService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(ORG, ITEM_ID, request(performedAt));

        assertThat(item.getLastPerformedAt()).isEqualTo(performedAt);
        assertThat(item.getNextDueAt()).isEqualTo(performedAt.plusMonths(6));
    }

    @Test
    void register_withNoPeriodConfigured_leavesNextDueAtUnchanged() {
        LocalDate performedAt = LocalDate.now();
        LocalDate preexistingNextDueAt = LocalDate.now().plusYears(1);
        MaintenanceItem item = MaintenanceItem.builder()
                .id(ITEM_ID).organizationCode(ORG).nextDueAt(preexistingNextDueAt).build();
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        when(maintenanceRepository.existsByItemIdAndPerformedAt(ITEM_ID, LocalDate.now())).thenReturn(false);
        when(authenticationService.getCurrentUser()).thenReturn(user());
        when(serviceBase.resolvePeriod(item)).thenReturn(null);
        when(maintenanceItemService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(ORG, ITEM_ID, request(performedAt));

        assertThat(item.getLastPerformedAt()).isEqualTo(performedAt);
        assertThat(item.getNextDueAt()).isEqualTo(preexistingNextDueAt); // comportamento pré-existente preservado
    }

    // BUGFIX (achado no QA manual, TASK-QA-MAN-011 C2): a checagem de duplicidade conferia
    // existsByItemIdAndPerformedAt(itemId, LocalDate.now()) — a data de HOJE — em vez de
    // req.performedAt(), a data que o usuário está de fato registrando. Os dois testes acima nunca
    // pegaram isso porque sempre usam performedAt = LocalDate.now(), onde as duas datas coincidem
    // por acidente. Aqui performedAt é uma data PASSADA, diferente de hoje — sem stub nenhum para
    // existsByItemIdAndPerformedAt(itemId, LocalDate.now()): se o código chamar com "hoje" em vez
    // da data do request, o teste falha com PotentialStubbingProblem/UnnecessaryStubbing, expondo
    // o bug com a mesma certeza que uma asserção explícita.
    @Test
    void register_checksConflictAgainstRequestedPerformedAt_notAgainstToday() {
        LocalDate pastPerformedAt = LocalDate.now().minusDays(5);
        MaintenanceItem item = MaintenanceItem.builder().id(ITEM_ID).organizationCode(ORG).build();
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        when(maintenanceRepository.existsByItemIdAndPerformedAt(ITEM_ID, pastPerformedAt)).thenReturn(false);
        when(authenticationService.getCurrentUser()).thenReturn(user());
        when(serviceBase.resolvePeriod(item)).thenReturn(Period.ofMonths(6));
        when(maintenanceItemService.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.register(ORG, ITEM_ID, request(pastPerformedAt));

        assertThat(item.getLastPerformedAt()).isEqualTo(pastPerformedAt);
    }

    @Test
    void register_throwsConflict_whenAnotherMaintenanceAlreadyExistsForThatSamePastDate() {
        LocalDate pastPerformedAt = LocalDate.now().minusDays(5);
        MaintenanceItem item = MaintenanceItem.builder().id(ITEM_ID).organizationCode(ORG).build();
        when(maintenanceItemService.findById(ITEM_ID)).thenReturn(item);
        when(maintenanceRepository.existsByItemIdAndPerformedAt(ITEM_ID, pastPerformedAt)).thenReturn(true);

        assertThatThrownBy(() -> service.register(ORG, ITEM_ID, request(pastPerformedAt)))
                .isInstanceOf(ConflictException.class);
    }
}
