package com.brainbyte.easy_maintenance.assets.infrastructure.persistence;

import com.brainbyte.easy_maintenance.assets.domain.MaintenanceAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface MaintenanceAttachmentRepository extends JpaRepository<MaintenanceAttachment, Long> {

    List<MaintenanceAttachment> findByMaintenanceId(Long maintenanceId);

    // TASK-142: busca os anexos de várias manutenções numa única query — usado por
    // findCancelledByItem, que lista N manutenções de uma vez (evita 1 query por manutenção).
    List<MaintenanceAttachment> findByMaintenanceIdIn(Collection<Long> maintenanceIds);

    boolean existsByFileUrl(String fileUrl);

    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM MaintenanceAttachment a " +
           "WHERE a.maintenanceId IN " +
           "(SELECT m.id FROM MaintenanceItem m WHERE m.organizationCode = :orgCode) " +
           "AND a.uploadedAt >= :startOfMonth")
    long sumSizeBytesByOrgSince(@Param("orgCode") String orgCode, @Param("startOfMonth") Instant startOfMonth);
}
