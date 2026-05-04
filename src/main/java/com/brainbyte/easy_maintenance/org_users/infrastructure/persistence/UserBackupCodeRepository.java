package com.brainbyte.easy_maintenance.org_users.infrastructure.persistence;

import com.brainbyte.easy_maintenance.org_users.domain.UserBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserBackupCodeRepository extends JpaRepository<UserBackupCode, Long> {

    List<UserBackupCode> findByUserIdAndUsedAtIsNull(Long userId);

    void deleteByUserId(Long userId);
}
