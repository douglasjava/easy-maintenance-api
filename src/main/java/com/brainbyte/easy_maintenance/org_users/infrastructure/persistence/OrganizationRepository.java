package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long>, JpaSpecificationExecutor<Organization> {

  boolean existsByCode(String code);

  List<Organization> findAllByCodeIn(java.util.Collection<String> codes);

  Optional<Organization> findByCode(String code);

  @Query("SELECT o FROM Organization o " +
          "JOIN UserOrganization uo ON o.code = uo.organizationCode " +
          "WHERE uo.user.id = :userId")
  List<Organization> findAllByUserId(@Param("userId") Long userId);

}
