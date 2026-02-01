package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

  boolean existsByCode(String code);

  java.util.List<com.brainbyte.easy_maintenance.org_users.domain.Organization> findAllByCodeIn(java.util.Collection<String> codes);

  long countByPlan(com.brainbyte.easy_maintenance.org_users.domain.enums.Plan plan);

}
