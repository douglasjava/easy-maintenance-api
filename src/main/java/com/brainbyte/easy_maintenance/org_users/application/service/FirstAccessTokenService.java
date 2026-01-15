package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.org_users.domain.FirstAccessToken;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.FirstAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FirstAccessTokenService {

    public final FirstAccessTokenRepository repository;

    public FirstAccessToken createForUser(Long userId) {
        log.info("Criando token para primeiro acesso {}", userId);

        String token = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = Instant.now().plus(48, ChronoUnit.HOURS);

        FirstAccessToken fat = new FirstAccessToken();
        fat.setUserId(userId);
        fat.setToken(token);
        fat.setExpiresAt(expiresAt);

        return repository.save(fat);
    }

    public boolean existsByUserIdAndUsedAtIsNull(Long userId) {
        return repository.existsByUserIdAndUsedAtIsNull(userId);
    }

    public Optional<FirstAccessToken> findByUserId(Long userId) {
        return repository.findByUserId(userId);
    }

    public void markUsed(FirstAccessToken fat) {
        fat.setUsedAt(Instant.now());
        repository.save(fat);
    }

}
