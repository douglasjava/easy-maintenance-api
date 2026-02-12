package com.brainbyte.easy_maintenance.push.application.service;

import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.push.mapper.IPushTokenMapper;
import com.brainbyte.easy_maintenance.push.application.dto.PushTokenResponse;
import com.brainbyte.easy_maintenance.push.application.dto.RegisterOrUpdateTokenRequest;
import com.brainbyte.easy_maintenance.push.domain.UserPushToken;
import com.brainbyte.easy_maintenance.push.infrastructure.repository.UserPushTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushTokenService {

    private final UserPushTokenRepository repository;
    private final UserRepository userRepository;

    @Transactional
    public PushTokenResponse registerOrUpdatePublic(RegisterOrUpdateTokenRequest request) {
        log.info("Received request to register or update public token");

        Instant now = Instant.now();
        String resolvedPlatform = resolvePlatform(request.platform());

        UserPushToken entity = repository.findByToken(request.token())
                .map(existing -> IPushTokenMapper.INSTANCE.updateExistingToken(existing, now, resolvedPlatform, request.endpoint(), request.device_info()))
                .orElseGet(() -> IPushTokenMapper.INSTANCE.createNewToken(request.token(), now, resolvedPlatform, request.endpoint(), request.device_info()));

        try {
            UserPushToken saved = repository.save(entity);
            return IPushTokenMapper.INSTANCE.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            return repository.findByToken(request.token())
                    .map(IPushTokenMapper.INSTANCE::toResponse)
                    .orElseThrow(() -> ex);
        }

    }

    @Transactional
    public PushTokenResponse linkToAuthenticatedUser(Authentication authentication, String token) {
        log.info("Received request to link to authenticated user {}", authentication.getName());

        String email = (String) authentication.getPrincipal();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário autenticado não encontrado"));

        Instant now = Instant.now();
        UserPushToken entity = repository.findByToken(token)
                .map(existing -> {
                    existing.setUser(user);
                    existing.setActive(true);
                    existing.setLastSeenAt(now);
                    return existing;
                })
                .orElseGet(() -> UserPushToken.builder()
                        .user(user)
                        .token(token)
                        .platform("WEB")
                        .active(true)
                        .lastSeenAt(now)
                        .build());

        UserPushToken saved = repository.save(entity);
        return new PushTokenResponse(saved.getId(), saved.getToken(), saved.getPlatform(), saved.isActive());
    }

    @Transactional
    public void disableToken(String token) {
        log.info("Received request to disable token {}", token);

        repository.findByToken(token).ifPresent(existing -> {
            existing.setActive(false);
            repository.save(existing);
        });

    }

    private String resolvePlatform(String platform) {
        return (platform == null || platform.isBlank()) ? "WEB" : platform;
    }

}
