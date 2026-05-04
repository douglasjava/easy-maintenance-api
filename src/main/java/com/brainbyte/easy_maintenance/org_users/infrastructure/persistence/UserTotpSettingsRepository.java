package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.UserTotpSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserTotpSettingsRepository extends JpaRepository<UserTotpSettings, Long> {

    Optional<UserTotpSettings> findByUserId(Long userId);

    boolean existsByUserIdAndEnabledTrue(Long userId);
}
