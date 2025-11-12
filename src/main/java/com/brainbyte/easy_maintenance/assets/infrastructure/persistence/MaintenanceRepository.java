package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.Maintenance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

  List<Maintenance> findByItemIdOrderByPerformedAtDesc(Long itemId);

}
