package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.FirstAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FirstAccessTokenRepository extends JpaRepository<FirstAccessToken, Long> {

    Optional<FirstAccessToken> findByUserId(Long userId);

    boolean existsByUserIdAndUsedAtIsNull(Long userId);

}
