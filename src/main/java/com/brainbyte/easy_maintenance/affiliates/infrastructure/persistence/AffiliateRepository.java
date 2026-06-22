package com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence;

import com.brainbyte.easy_maintenance.affiliates.domain.Affiliate;
import com.brainbyte.easy_maintenance.affiliates.domain.AffiliateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AffiliateRepository extends JpaRepository<Affiliate, Long> {
    Optional<Affiliate> findByCode(String code);
    Optional<Affiliate> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Affiliate> findAllByStatus(AffiliateStatus status);
}
