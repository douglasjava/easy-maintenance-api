package com.brainbyte.easy_maintenance.infrastructure.notification.repository;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    List<InAppNotification> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE InAppNotification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.readAt IS NULL")
    void markAllReadByUserId(@Param("userId") Long userId);
}
