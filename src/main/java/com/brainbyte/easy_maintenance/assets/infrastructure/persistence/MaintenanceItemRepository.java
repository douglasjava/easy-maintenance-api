package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceItem;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MaintenanceItemRepository extends JpaRepository<MaintenanceItem, Long> {

  Page<MaintenanceItem> findByOrganizationCodeAndStatus(String orgId, ItemStatus status, Pageable pageable);

  List<MaintenanceItem> findByNextDueAtLessThanEqual(LocalDate date);

  Page<MaintenanceItem> findByOrganizationCode(String orgId, Pageable pageable);

  Page<MaintenanceItem> findByOrganizationCodeAndItemType(String orgId, String itemType, Pageable pageable);

  Page<MaintenanceItem> findByOrganizationCodeAndStatusAndItemType(String orgId, ItemStatus status, String itemType, Pageable pageable);

  long countByOrganizationCode(String orgId);

  long countByOrganizationCodeAndStatus(String orgId, ItemStatus status);
}



