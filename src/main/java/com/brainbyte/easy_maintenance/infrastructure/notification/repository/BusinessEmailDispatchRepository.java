package com.brainbyte.easy_maintenance.infrastructure.notification.repository;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessEmailDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface BusinessEmailDispatchRepository extends JpaRepository<BusinessEmailDispatch, Long> {

    @Query("SELECT COUNT(d) FROM BusinessEmailDispatch d " +
            "WHERE d.organizationCode = :orgCode " +
            "AND d.status = 'SENT' " +
            "AND d.sentAt >= :startOfMonth")
    long countSentInMonth(@Param("orgCode") String organizationCode, @Param("startOfMonth") Instant startOfMonth);
}
