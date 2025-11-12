package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

  Optional<User> findByOrganizationCodeAndId(String organizationCode, Long id);

  boolean existsByOrganizationCode(String OrganizationCode);

}
