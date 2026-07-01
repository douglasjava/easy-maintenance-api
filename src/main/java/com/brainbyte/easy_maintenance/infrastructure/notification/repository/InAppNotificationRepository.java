package com.brainbyte.easy_maintenance.infrastructure.notification.repository;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.InAppNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {

    // ── fallback: sem org (usado quando TenantContext não está presente) ──────
    List<InAppNotification> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNull(Long userId);

    @Modifying
    @Transactional
    @Query("UPDATE InAppNotification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.readAt IS NULL")
    void markAllReadByUserId(@Param("userId") Long userId);

    // ── org-scoped: org ativa + notificações pessoais (orgCode IS NULL) ───────
    @Query("""
        SELECT n FROM InAppNotification n
        WHERE n.userId = :userId
          AND (n.orgCode = :orgCode OR n.orgCode IS NULL)
        ORDER BY n.createdAt DESC
    """)
    List<InAppNotification> findTop20ForUserAndOrg(
        @Param("userId") Long userId,
        @Param("orgCode") String orgCode,
        Pageable pageable
    );

    @Query("""
        SELECT COUNT(n) FROM InAppNotification n
        WHERE n.userId = :userId
          AND n.readAt IS NULL
          AND (n.orgCode = :orgCode OR n.orgCode IS NULL)
    """)
    long countUnreadForUserAndOrg(
        @Param("userId") Long userId,
        @Param("orgCode") String orgCode
    );

    @Modifying
    @Transactional
    @Query("""
        UPDATE InAppNotification n
        SET n.readAt = CURRENT_TIMESTAMP
        WHERE n.userId = :userId
          AND n.readAt IS NULL
          AND (n.orgCode = :orgCode OR n.orgCode IS NULL)
    """)
    void markAllReadForUserAndOrg(
        @Param("userId") Long userId,
        @Param("orgCode") String orgCode
    );
}
