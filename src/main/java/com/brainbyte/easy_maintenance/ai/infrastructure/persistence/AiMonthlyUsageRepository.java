package com.brainbyte.easy_maintenance.ai.infrastructure.persistence;

import com.brainbyte.easy_maintenance.ai.domain.AiMonthlyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AiMonthlyUsageRepository extends JpaRepository<AiMonthlyUsage, Long> {

    Optional<AiMonthlyUsage> findByUserIdAndUsageMonth(Long userId, String usageMonth);

}
