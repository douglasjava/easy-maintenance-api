package com.brainbyte.easy_maintenance.infrastructure.notification.repository;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessEmailDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessEmailDispatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BusinessEmailDispatchRepository extends JpaRepository<BusinessEmailDispatch, Long> {

    @Query("SELECT COUNT(d) FROM BusinessEmailDispatch d " +
            "WHERE d.organizationCode = :orgCode " +
            "AND d.status = 'SENT' " +
            "AND d.sentAt >= :startOfMonth")
    long countSentInMonth(@Param("orgCode") String organizationCode, @Param("startOfMonth") Instant startOfMonth);

    @Query("SELECT d FROM BusinessEmailDispatch d " +
            "WHERE d.status = :status " +
            "AND d.retryable = true " +
            "AND d.retryCount < :maxRetries " +
            "AND d.createdAt >= :cutoff " +
            "AND (d.lastRetryAt IS NULL OR d.lastRetryAt <= :lastRetryBefore)")
    List<BusinessEmailDispatch> findEligibleForRetry(
            @Param("status") BusinessEmailDispatchStatus status,
            @Param("maxRetries") int maxRetries,
            @Param("cutoff") Instant cutoff,
            @Param("lastRetryBefore") Instant lastRetryBefore);
}
