package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserOrganizationRepository extends JpaRepository<UserOrganization, Long> {
    Optional<UserOrganization> findByUserIdAndOrganizationCode(Long userId, String organizationCode);
    void deleteByUserIdAndOrganizationCode(Long userId, String organizationCode);
}
