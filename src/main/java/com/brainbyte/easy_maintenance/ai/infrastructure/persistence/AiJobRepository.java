package com.brainbyte.easy_maintenance.ai.infrastructure.persistence;

import com.brainbyte.easy_maintenance.ai.domain.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface AiJobRepository extends JpaRepository<AiJob, String> {

    @Modifying
    @Transactional
    @Query("DELETE FROM AiJob j WHERE j.completedAt IS NOT NULL AND j.completedAt < :cutoff")
    int deleteCompletedBefore(@Param("cutoff") Instant cutoff);
}
