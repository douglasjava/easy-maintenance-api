package com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence;

import com.brainbyte.easy_maintenance.affiliates.domain.CommissionStatus;
import com.brainbyte.easy_maintenance.affiliates.domain.ReferralCommission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralCommissionRepository extends JpaRepository<ReferralCommission, Long> {
    boolean existsByOrganizationId(Long organizationId);
    List<ReferralCommission> findAllByAffiliateId(Long affiliateId);
    List<ReferralCommission> findAllByStatus(CommissionStatus status);
    List<ReferralCommission> findAllByOrderByCreatedAtDesc();
}
