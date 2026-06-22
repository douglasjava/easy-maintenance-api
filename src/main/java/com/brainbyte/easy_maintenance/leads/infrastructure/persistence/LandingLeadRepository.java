package com.brainbyte.easy_maintenance.leads.infrastructure.persistence;

import com.brainbyte.easy_maintenance.leads.domain.LandingLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LandingLeadRepository extends JpaRepository<LandingLead, Long> {
    Optional<LandingLead> findFirstByEmailAndAffiliateCodeIsNotNull(String email);
    List<LandingLead> findAllByAffiliateCode(String affiliateCode);
}

