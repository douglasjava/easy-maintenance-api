package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long>, JpaSpecificationExecutor<Maintenance> {

  @Query("select count(m) from Maintenance m join com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem i on i.id = m.itemId where i.organizationCode = :org and m.performedAt between :start and :end")
  long countByOrgAndPerformedBetween(@Param("org") String orgId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query(value = "select cast(avg(greatest(0, datediff(m.performed_at, i.next_due_at))) as signed) from maintenances m join maintenance_items i on i.id = m.item_id where i.organization_code = :org and m.created_at >= :since", nativeQuery = true)
  Integer avgDaysToResolveLast90(@Param("org") String orgId, @Param("since") Instant since);

  boolean existsByItemIdAndPerformedAt(Long itemId, LocalDate performedAt);

}
