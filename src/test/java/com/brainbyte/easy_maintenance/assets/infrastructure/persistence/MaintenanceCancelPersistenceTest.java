package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reprodução real (não mockada) do bug relatado por Douglas no QA manual (C1 da TASK-QA-MAN-011):
 * cancelar uma manutenção não persistia cancelled_at/cancelled_by/cancel_reason, só deleted_at.
 *
 * Testes com Mockito (MaintenanceCancelTest) não pegam isso porque verificam só que
 * repository.save(captor.capture()) foi chamado com um objeto Java com os campos certos em
 * memória — nunca exercitam o flush/dirty-checking real do Hibernate. Este teste usa H2 real
 * (@DataJpaTest, sem Flyway — ddl-auto gera o schema a partir das anotações JPA, suficiente pra
 * isolar o comportamento de @SQLDelete + save()+delete() na mesma transação).
 */
// Escopo restrito de propósito a Maintenance/MaintenanceRepository (@EntityScan/@EnableJpaRepositories
// explícitos) — sem isso, @DataJpaTest tenta validar TODAS as queries JPQL de TODOS os repositórios
// da aplicação contra o H2, e pelo menos um repositório não relacionado (InAppNotificationRepository)
// tem uma query que não é compatível com H2, derrubando o contexto por um motivo que não tem nada a
// ver com o que este teste investiga.
@DataJpaTest
@EntityScan(basePackageClasses = Maintenance.class)
@EnableJpaRepositories(basePackageClasses = MaintenanceRepository.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MaintenanceCancelPersistenceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MaintenanceRepository maintenanceRepository;

    @Test
    void cancel_persistsReasonAuthorAndTimestamp_evenThoughSoftDeleteRunsRightAfter() {
        Maintenance maintenance = Maintenance.builder()
                .itemId(1L)
                .performedAt(LocalDate.now())
                .type(MaintenanceType.PREVENTIVA)
                .build();
        entityManager.persistAndFlush(maintenance);
        Long id = maintenance.getId();

        // Exatamente a sequência de MaintenanceService.cancel() (TASK-137):
        maintenance.setCancelledAt(Instant.now());
        maintenance.setCancelledBy(42L);
        maintenance.setCancelReason("Reprodução do bug relatado no QA manual");
        maintenanceRepository.saveAndFlush(maintenance);
        maintenanceRepository.delete(maintenance);

        entityManager.flush();
        entityManager.clear(); // limpa o cache de 1º nível — próxima leitura vai direto ao banco

        // Query nativa: @SQLRestriction("deleted_at IS NULL") esconderia a linha de qualquer busca
        // JPQL/Criteria normal, já que deleted_at agora está preenchido.
        EntityManager em = entityManager.getEntityManager();
        Object[] row = (Object[]) em.createNativeQuery(
                        "SELECT deleted_at, cancelled_at, cancelled_by, cancel_reason FROM maintenances WHERE id = ?1")
                .setParameter(1, id)
                .getSingleResult();

        assertThat(row[0]).as("deleted_at deveria estar preenchido (soft-delete)").isNotNull();
        assertThat(row[1]).as("cancelled_at deveria ter sido persistido").isNotNull();
        assertThat(row[2]).as("cancelled_by deveria ter sido persistido").isNotNull();
        assertThat(row[3]).as("cancel_reason deveria ter sido persistido").isEqualTo("Reprodução do bug relatado no QA manual");
    }

    @Test
    void existsByItemIdAndPerformedAt_ignoresCancelledMaintenance() {
        LocalDate today = LocalDate.now();
        Maintenance maintenance = Maintenance.builder()
                .itemId(1L)
                .performedAt(today)
                .type(MaintenanceType.PREVENTIVA)
                .build();
        entityManager.persistAndFlush(maintenance);

        maintenance.setCancelledAt(Instant.now());
        maintenance.setCancelledBy(42L);
        maintenance.setCancelReason("Cancelada para reprodução do bug de re-registro no mesmo dia");
        maintenanceRepository.saveAndFlush(maintenance);
        maintenanceRepository.delete(maintenance);

        entityManager.flush();
        entityManager.clear();

        boolean exists = maintenanceRepository.existsByItemIdAndPerformedAt(1L, today);

        assertThat(exists)
                .as("uma manutenção cancelada não deveria contar para a checagem de duplicidade do dia")
                .isFalse();
    }

    // TASK-145 (EPIC-017): prova com banco real (join maintenances + maintenance_items) que
    // findCancelledByOrgAndPeriod isola por organização de verdade — um teste Mockito só provaria
    // que os parâmetros certos foram passados pro repositório, não que o JOIN/WHERE da query nativa
    // está correto (ex.: coluna errada, join errado). MaintenanceItem está no mesmo pacote de
    // Maintenance, então já está coberto pelo @EntityScan existente desta classe.
    @Test
    void findCancelledByOrgAndPeriod_isolatesByOrganization_andIgnoresOutOfRangeDates() {
        MaintenanceItem itemOrgA = MaintenanceItem.builder().organizationCode("ORG-A").itemType("Extintor").build();
        MaintenanceItem itemOrgB = MaintenanceItem.builder().organizationCode("ORG-B").itemType("Gerador").build();
        entityManager.persistAndFlush(itemOrgA);
        entityManager.persistAndFlush(itemOrgB);

        LocalDate inRange = LocalDate.of(2026, 6, 15);
        LocalDate outOfRange = LocalDate.of(2026, 1, 1);

        Maintenance cancelledOrgAInRange = cancelledMaintenance(itemOrgA.getId(), inRange);
        Maintenance cancelledOrgBInRange = cancelledMaintenance(itemOrgB.getId(), inRange);
        Maintenance cancelledOrgAOutOfRange = cancelledMaintenance(itemOrgA.getId(), outOfRange);

        entityManager.clear();

        List<Maintenance> result = maintenanceRepository.findCancelledByOrgAndPeriod(
                "ORG-A", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(result)
                .as("só a cancelada de ORG-A dentro do período deveria voltar — nem a de ORG-B, nem a fora do período")
                .extracting(Maintenance::getId)
                .containsExactly(cancelledOrgAInRange.getId());
    }

    private Maintenance cancelledMaintenance(Long itemId, LocalDate performedAt) {
        Maintenance maintenance = Maintenance.builder()
                .itemId(itemId)
                .performedAt(performedAt)
                .type(MaintenanceType.PREVENTIVA)
                .build();
        entityManager.persistAndFlush(maintenance);

        maintenance.setCancelledAt(Instant.now());
        maintenance.setCancelledBy(1L);
        maintenance.setCancelReason("Reprodução TASK-145");
        maintenanceRepository.saveAndFlush(maintenance);
        maintenanceRepository.delete(maintenance);
        entityManager.flush();
        return maintenance;
    }
}
