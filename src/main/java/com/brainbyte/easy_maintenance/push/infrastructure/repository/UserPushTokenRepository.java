package com.brainbyte.easy_maintenance.push.infrastructure.repository;

import com.brainbyte.easy_maintenance.push.domain.UserPushToken;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPushTokenRepository extends JpaRepository<UserPushToken, Long> {

    Optional<UserPushToken> findByToken(String token);

    boolean existsByToken(String token);

    List<UserPushToken> findAllByUserAndActiveIsTrue(User user);
}
