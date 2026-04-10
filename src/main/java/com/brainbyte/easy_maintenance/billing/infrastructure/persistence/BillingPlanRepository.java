package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingPlanRepository extends JpaRepository<BillingPlan, Long> {

    Optional<BillingPlan> findByCode(String code);

    List<BillingPlan> findAllByStatus(BillingStatus status);

}
