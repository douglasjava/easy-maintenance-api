package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = "organizations")
    @Query("""
              SELECT u
              FROM User u
              JOIN u.organizations o
              WHERE o.organizationCode = :organizationCode
              AND u.id = :id
            """)
    Optional<User> findByOrganizationCodeAndId(String organizationCode, Long id);

    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.organizations o WHERE o.organizationCode = :organizationCode")
    boolean existsByOrganizationCode(String organizationCode);

    @EntityGraph(attributePaths = "organizations")
    @Query("""
              SELECT u
              FROM User u
              JOIN u.organizations o
              WHERE o.organizationCode = :organizationCode
            """)
    Page<User> findAllByOrganizationCode(@Param("organizationCode") String organizationCode, Pageable pageable);


    @EntityGraph(attributePaths = "organizations")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @EntityGraph(attributePaths = "organizations")
    @Query("""
              SELECT u
              FROM User u
              JOIN u.organizations o
              WHERE u.id = :id
            """)
    Optional<User> findByIdWithOrganization(Long id);

}
