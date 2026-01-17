package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.dashboard.infrastructure.persistence.DashboardAggregations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MaintenanceItemRepository extends JpaRepository<MaintenanceItem, Long>, JpaSpecificationExecutor<MaintenanceItem> {

  Page<MaintenanceItem> findByOrganizationCode(String orgId, Pageable pageable);

  long countByOrganizationCode(String orgId);

  long countByOrganizationCodeAndStatus(String orgId, ItemStatus status);

  @Query("select mi.status as status, count(mi) as cnt from MaintenanceItem mi where mi.organizationCode = :org group by mi.status")
  List<DashboardAggregations.CountByStatus> countByStatus(@Param("org") String orgId);

  @Query("select mi.itemCategory as itemCategory, count(mi) as cnt from MaintenanceItem mi where mi.organizationCode = :org group by mi.itemCategory")
  List<DashboardAggregations.CountByCategory> countByCategory(@Param("org") String orgId);

  @Query("select mi.itemType as itemType, count(mi) as cnt from MaintenanceItem mi where mi.organizationCode = :org group by mi.itemType order by count(mi) desc")
  List<DashboardAggregations.CountByItemType> topByItemType(@Param("org") String orgId, Pageable pageable);

  @Query("select mi from MaintenanceItem mi where mi.organizationCode = :org and mi.nextDueAt between :start and :end")
  List<MaintenanceItem> findUpcoming(@Param("org") String orgId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query("select mi from MaintenanceItem mi where mi.organizationCode = :org and (mi.status = com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus.OVERDUE or mi.status = com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus.NEAR_DUE)")
  List<MaintenanceItem> findAttentionCandidates(@Param("org") String orgId);

  @Query("select count(mi) from MaintenanceItem mi where mi.organizationCode = :org and mi.nextDueAt between :start and :end")
  long countDueBetween(@Param("org") String orgId, @Param("start") LocalDate start, @Param("end") LocalDate end);

  @Query("select count(mi) from MaintenanceItem mi where mi.organizationCode = :org and mi.nextDueAt between :start and :end")
  long countDueSoon(@Param("org") String orgId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}



