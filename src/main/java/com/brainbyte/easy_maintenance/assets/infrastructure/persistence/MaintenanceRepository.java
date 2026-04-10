package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceExportProjection;
import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long>, JpaSpecificationExecutor<Maintenance> {

  @Query("select count(m) from Maintenance m join com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem i on i.id = m.itemId where i.organizationCode = :org and m.performedAt between :start and :end")
  long countByOrgAndPerformedBetween(@Param("org") String orgId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(value = "select cast(avg(greatest(0, datediff(m.performed_at, i.next_due_at))) as signed) from maintenances m join maintenance_items i on i.id = m.item_id where i.organization_code = :org and m.created_at >= :since", nativeQuery = true)
  Integer avgDaysToResolveLast90(@Param("org") String orgId, @Param("since") Instant since);

  boolean existsByItemIdAndPerformedAt(Long itemId, LocalDate performedAt);

  boolean existsByItemId(Long itemId);

  @Query("SELECT m FROM Maintenance m JOIN com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem i ON i.id = m.itemId WHERE m.nextDueAt IN :dates")
  List<Maintenance> findAllByNextDueAtIn(@Param("dates") java.util.Collection<LocalDate> dates);

  @Query(value =
      "SELECT m.id AS id, i.item_type AS itemType, m.performed_at AS performedAt, " +
      "m.type AS maintenanceType, m.performed_by AS performedBy, m.cost_cents AS costCents, " +
      "m.next_due_at AS nextDueAt, n.authority AS normAuthority " +
      "FROM maintenances m " +
      "JOIN maintenance_items i ON i.id = m.item_id " +
      "LEFT JOIN norms n ON n.id = i.norm_id " +
      "WHERE i.organization_code = :orgCode " +
      "AND (:itemId IS NULL OR m.item_id = :itemId) " +
      "AND (:startDate IS NULL OR m.performed_at >= :startDate) " +
      "AND (:endDate IS NULL OR m.performed_at <= :endDate) " +
      "ORDER BY m.performed_at DESC " +
      "LIMIT 5000",
      nativeQuery = true)
  List<MaintenanceExportProjection> findForExport(
      @Param("orgCode") String orgCode,
      @Param("itemId") Long itemId,
      @Param("startDate") LocalDate startDate,
      @Param("endDate") LocalDate endDate
  );

}
