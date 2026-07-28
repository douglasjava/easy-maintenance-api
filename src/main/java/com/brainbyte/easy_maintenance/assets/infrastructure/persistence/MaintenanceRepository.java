package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.application.dto.CrossOrgMaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long>, JpaSpecificationExecutor<Maintenance> {

  @Query("select count(m) from Maintenance m join com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem i on i.id = m.itemId where i.organizationCode = :org and m.performedAt between :start and :end")
  long countByOrgAndPerformedBetween(@Param("org") String orgId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  // TASK-139: query nativa — @SQLRestriction só se aplica a queries JPQL/Criteria geradas pelo
  // Hibernate, não a nativeQuery. Sem "m.deleted_at IS NULL" explícito, essa média de dias pra
  // resolver passaria a contar manutenções canceladas (TASK-137) assim que existirem, distorcendo
  // o KPI do dashboard.
  @Query(value = "select cast(avg(greatest(0, datediff(m.performed_at, i.next_due_at))) as signed) from maintenances m join maintenance_items i on i.id = m.item_id where i.organization_code = :org and m.created_at >= :since and m.deleted_at is null", nativeQuery = true)
  Integer avgDaysToResolveLast90(@Param("org") String orgId, @Param("since") Instant since);

  boolean existsByItemIdAndPerformedAt(Long itemId, LocalDate performedAt);

  boolean existsByItemId(Long itemId);

  // TASK-137: usado só pra distinguir "nunca existiu" (404) de "já cancelada" (409) no cancelamento
  // — @SQLRestriction já filtra canceladas de findById, então essa checagem precisa de query nativa
  // pra enxergar além da restrição. Filtro por organization_code embutido de propósito: sem isso, um
  // usuário de outra organização poderia usar a diferença 404/409 pra descobrir se um ID de
  // manutenção de outra empresa existe e foi cancelado (leak de existência cross-tenant).
  @Query(value = "SELECT COUNT(*) > 0 FROM maintenances m " +
      "JOIN maintenance_items i ON i.id = m.item_id " +
      "WHERE m.id = :id AND m.deleted_at IS NOT NULL AND i.organization_code = :orgCode",
      nativeQuery = true)
  boolean existsCancelledByIdAndOrgCode(@Param("id") Long id, @Param("orgCode") String orgCode);

  // TASK-138: manutenção válida mais recente do item, usada pra recalcular nextDueAt/lastPerformedAt
  // do item depois de um cancelamento. @SQLRestriction("deleted_at IS NULL") já garante que
  // canceladas nunca aparecem aqui — não precisa de filtro explícito. Desempate por id DESC quando
  // duas manutenções têm o mesmo performedAt (mesma regra descrita na TASK-138).
  Optional<Maintenance> findFirstByItemIdOrderByPerformedAtDescIdDesc(Long itemId);

  // TASK-139: única query que DEVE ver canceladas de propósito — usada pra exibi-las separadamente
  // na tela do item, nunca misturadas com a listagem/detalhe padrão. Query nativa (não JPQL) porque
  // aqui o objetivo é justamente contornar o @SQLRestriction, o oposto de todas as outras queries
  // deste repositório.
  @Query(value = "SELECT * FROM maintenances WHERE item_id = :itemId AND deleted_at IS NOT NULL " +
      "ORDER BY performed_at DESC",
      nativeQuery = true)
  List<Maintenance> findCancelledByItemId(@Param("itemId") Long itemId);

  // TASK-145 (EPIC-017): mesma lógica de findCancelledByItemId, mas escopada à organização inteira
  // num período — pré-requisito da seção de auditoria do Relatório de Prestação de Contas. Query
  // nativa pelo mesmo motivo de avgDaysToResolveLast90: @SQLRestriction não se aplica a nativeQuery,
  // então precisamos do "deleted_at IS NOT NULL" explícito pra enxergar só as canceladas. Filtro de
  // organização embutido na própria query (mesmo cuidado da TASK-137/139 — nunca checagem posterior).
  @Query(value = "SELECT m.* FROM maintenances m " +
      "JOIN maintenance_items i ON i.id = m.item_id " +
      "WHERE i.organization_code = :orgCode " +
      "AND m.deleted_at IS NOT NULL " +
      "AND m.performed_at BETWEEN :from AND :to " +
      "ORDER BY m.performed_at DESC",
      nativeQuery = true)
  List<Maintenance> findCancelledByOrgAndPeriod(@Param("orgCode") String orgCode,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

  // Batch check: returns the subset of the given IDs that have at least one maintenance record.
  // Used by the list endpoint to resolve canUpdate for an entire page in a single query.
  @Query("SELECT DISTINCT m.itemId FROM Maintenance m WHERE m.itemId IN :ids")
  Set<Long> findItemIdsWithMaintenances(@Param("ids") Collection<Long> ids);

  @Query("SELECT m FROM Maintenance m JOIN com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem i ON i.id = m.itemId WHERE m.nextDueAt IN :dates")
  List<Maintenance> findAllByNextDueAtIn(@Param("dates") java.util.Collection<LocalDate> dates);

  // TASK-139: query nativa, @SQLRestriction não se aplica — "m.deleted_at IS NULL" explícito é
  // obrigatório aqui, senão o export CSV passaria a incluir manutenções canceladas (TASK-137).
  @Query(value =
      "SELECT m.id AS id, i.item_type AS itemType, m.performed_at AS performedAt, " +
      "m.type AS maintenanceType, m.performed_by AS performedBy, m.cost_cents AS costCents, " +
      "m.next_due_at AS nextDueAt, n.authority AS normAuthority, i.item_category AS itemCategory, " +
      "m.created_by AS createdBy " +
      "FROM maintenances m " +
      "JOIN maintenance_items i ON i.id = m.item_id " +
      "LEFT JOIN norms n ON n.id = i.norm_id " +
      "WHERE i.organization_code = :orgCode " +
      "AND m.deleted_at IS NULL " +
      "AND (:itemId IS NULL OR m.item_id = :itemId) " +
      "AND (:startDate IS NULL OR m.performed_at >= :startDate) " +
      "AND (:endDate IS NULL OR m.performed_at <= :endDate) " +
      "AND (:performedBy IS NULL OR m.performed_by LIKE CONCAT('%', :performedBy, '%')) " +
      "ORDER BY m.performed_at DESC " +
      "LIMIT 5000",
      nativeQuery = true)
  List<MaintenanceExportProjection> findForExport(
      @Param("orgCode") String orgCode,
      @Param("itemId") Long itemId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("performedBy") String performedBy
  );

  // TASK-139: mesmo motivo do findForExport acima — query nativa, filtro de deleted_at explícito
  // obrigatório.
  @Query(value =
      "SELECT m.id AS id, i.organization_code AS orgCode, i.item_type AS itemType, " +
      "m.performed_at AS performedAt, m.type AS maintenanceType, m.performed_by AS performedBy, " +
      "m.cost_cents AS costCents, m.next_due_at AS nextDueAt, n.authority AS normAuthority, " +
      "i.item_category AS itemCategory, m.created_by AS createdBy " +
      "FROM maintenances m " +
      "JOIN maintenance_items i ON i.id = m.item_id " +
      "LEFT JOIN norms n ON n.id = i.norm_id " +
      "WHERE i.organization_code IN (:orgCodes) " +
      "AND m.deleted_at IS NULL " +
      "AND (:startDate IS NULL OR m.performed_at >= :startDate) " +
      "AND (:endDate IS NULL OR m.performed_at <= :endDate) " +
      "AND (:type IS NULL OR m.type = :type) " +
      "AND (:itemType IS NULL OR i.item_type LIKE CONCAT('%', :itemType, '%')) " +
      "ORDER BY i.organization_code, m.performed_at DESC " +
      "LIMIT 5000",
      nativeQuery = true)
  List<CrossOrgMaintenanceExportProjection> findForExportCrossOrg(
      @Param("orgCodes") List<String> orgCodes,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate,
      @Param("type") String type,
      @Param("itemType") String itemType
  );

}
