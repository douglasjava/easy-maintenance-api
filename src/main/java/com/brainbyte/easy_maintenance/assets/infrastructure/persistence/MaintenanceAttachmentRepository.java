package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaintenanceAttachmentRepository extends JpaRepository<MaintenanceAttachment, Long> {
    List<MaintenanceAttachment> findByMaintenanceId(Long maintenanceId);
}
