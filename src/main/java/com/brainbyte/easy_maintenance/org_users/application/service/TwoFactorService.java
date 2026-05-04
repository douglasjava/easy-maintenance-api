package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.CriticalEmailDispatchService;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserBackupCode;
import com.brainbyte.easy_maintenance.org_users.domain.UserTotpSettings;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserBackupCodeRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserTotpSettingsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

    private static final long RECOVERY_TOKEN_TTL_HOURS = 1;

    private final TotpService totpService;
    private final UserTotpSettingsRepository totpSettingsRepository;
    private final UserBackupCodeRepository backupCodeRepository;
    private final UserRepository userRepository;
    private final CriticalEmailDispatchService criticalEmailDispatchService;
    private final EmailTemplateHelper emailTemplateHelper;

    @Value("${frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    public UserDTO.TwoFactorStatusResponse getStatus(Long userId) {
        return totpSettingsRepository.findByUserId(userId)
                .map(s -> new UserDTO.TwoFactorStatusResponse(
                        s.isEnabled(),
                        s.isEnabled() ? backupCodeRepository.findByUserIdAndUsedAtIsNull(userId).size() : 0))
                .orElse(new UserDTO.TwoFactorStatusResponse(false, 0));
    }

    @Transactional
    public UserDTO.TwoFactorSetupResponse initiateSetup(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        if (totpSettingsRepository.existsByUserIdAndEnabledTrue(userId)) {
            throw new ConflictException("2FA já está habilitado para este usuário. Desabilite primeiro.");
        }

        String secret = totpService.generateSecret();

        // Upsert pending settings (not yet enabled)
        UserTotpSettings settings = totpSettingsRepository.findByUserId(userId)
                .orElse(UserTotpSettings.builder()
                        .userId(userId)
                        .createdAt(Instant.now())
                        .build());
        settings.setTotpSecret(secret);
        settings.setEnabled(false);
        settings.setUpdatedAt(Instant.now());
        totpSettingsRepository.save(settings);

        String qrCodeDataUri = totpService.buildQrCodeDataUri(user.getEmail(), secret);
        String otpAuthUri = totpService.buildOtpAuthUri(user.getEmail(), secret);

        log.info("2FA setup initiated for user {}", userId);
        return new UserDTO.TwoFactorSetupResponse(secret, qrCodeDataUri, otpAuthUri);
    }

    @Transactional
    public UserDTO.TwoFactorConfirmResponse confirmSetup(Long userId, String code) {
        UserTotpSettings settings = totpSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuleException("Setup não iniciado. Chame /setup primeiro."));

        if (settings.isEnabled()) {
            throw new ConflictException("2FA já está habilitado.");
        }

        if (!totpService.verifyCode(settings.getTotpSecret(), code)) {
            throw new RuleException("Código TOTP inválido. Verifique o app autenticador e tente novamente.");
        }

        settings.setEnabled(true);
        settings.setUpdatedAt(Instant.now());
        totpSettingsRepository.save(settings);

        List<String> rawCodes = generateAndSaveBackupCodes(userId);

        log.info("2FA enabled for user {}", userId);
        return new UserDTO.TwoFactorConfirmResponse(rawCodes);
    }

    @Transactional
    public void disable(Long userId, String code) {
        UserTotpSettings settings = totpSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuleException("2FA não está configurado para este usuário."));

        if (!settings.isEnabled()) {
            throw new RuleException("2FA não está habilitado.");
        }

        boolean validTotp = totpService.verifyCode(settings.getTotpSecret(), code);
        boolean validBackup = !validTotp && consumeBackupCode(userId, code);

        if (!validTotp && !validBackup) {
            throw new RuleException("Código inválido. Use o código do autenticador ou um código de backup.");
        }

        settings.setEnabled(false);
        settings.setUpdatedAt(Instant.now());
        totpSettingsRepository.save(settings);
        backupCodeRepository.deleteByUserId(userId);

        log.info("2FA disabled for user {}", userId);
    }

    public boolean verifyForLogin(Long userId, String code) {
        UserTotpSettings settings = totpSettingsRepository.findByUserId(userId)
                .orElse(null);
        if (settings == null || !settings.isEnabled()) return true;

        if (totpService.verifyCode(settings.getTotpSecret(), code)) return true;
        return consumeBackupCode(userId, code);
    }

    public boolean isEnabled(Long userId) {
        return totpSettingsRepository.existsByUserIdAndEnabledTrue(userId);
    }

    @Transactional
    public List<String> regenerateBackupCodes(Long userId, String code) {
        UserTotpSettings settings = totpSettingsRepository.findByUserId(userId)
                .orElseThrow(() -> new RuleException("2FA não está configurado."));

        if (!settings.isEnabled()) {
            throw new RuleException("2FA não está habilitado.");
        }

        if (!totpService.verifyCode(settings.getTotpSecret(), code)) {
            throw new RuleException("Código TOTP inválido.");
        }

        backupCodeRepository.deleteByUserId(userId);
        return generateAndSaveBackupCodes(userId);
    }

    @Transactional
    public void requestEmailRecovery(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            UserTotpSettings settings = totpSettingsRepository.findByUserId(user.getId())
                    .orElse(null);
            if (settings == null || !settings.isEnabled()) return;

            String token = UUID.randomUUID().toString();
            settings.setRecoveryToken(token);
            settings.setRecoveryExpiresAt(Instant.now().plus(RECOVERY_TOKEN_TTL_HOURS, ChronoUnit.HOURS));
            settings.setUpdatedAt(Instant.now());
            totpSettingsRepository.save(settings);

            String recoveryLink = frontendBaseUrl + "/auth/2fa-recovery?token=" + token + "&email=" + email;
            String html = emailTemplateHelper.generateTwoFactorRecoveryHtml(user.getName(), recoveryLink);

            criticalEmailDispatchService.send(
                    user.getEmail(), user.getName(), null,
                    NotificationEventType.TWO_FACTOR_RECOVERY,
                    "Recuperação de 2FA - Easy Maintenance",
                    html, false);

            log.info("2FA recovery email sent for user {}", user.getId());
        });
    }

    @Transactional
    public void applyEmailRecovery(String email, String token) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuleException("Token de recuperação inválido ou expirado."));

        UserTotpSettings settings = totpSettingsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuleException("Token de recuperação inválido ou expirado."));

        if (settings.getRecoveryToken() == null
                || !settings.getRecoveryToken().equals(token)
                || settings.getRecoveryExpiresAt() == null
                || Instant.now().isAfter(settings.getRecoveryExpiresAt())) {
            throw new RuleException("Token de recuperação inválido ou expirado.");
        }

        settings.setEnabled(false);
        settings.setRecoveryToken(null);
        settings.setRecoveryExpiresAt(null);
        settings.setUpdatedAt(Instant.now());
        totpSettingsRepository.save(settings);
        backupCodeRepository.deleteByUserId(user.getId());

        log.info("2FA disabled via email recovery for user {}", user.getId());
    }

    private List<String> generateAndSaveBackupCodes(Long userId) {
        List<String> rawCodes = totpService.generateBackupCodes();
        Instant now = Instant.now();
        List<UserBackupCode> entities = rawCodes.stream()
                .map(raw -> UserBackupCode.builder()
                        .userId(userId)
                        .codeHash(totpService.hashBackupCode(raw))
                        .createdAt(now)
                        .build())
                .toList();
        backupCodeRepository.saveAll(entities);
        return rawCodes;
    }

    private boolean consumeBackupCode(Long userId, String rawCode) {
        List<UserBackupCode> unused = backupCodeRepository.findByUserIdAndUsedAtIsNull(userId);
        for (UserBackupCode bc : unused) {
            if (totpService.verifyBackupCode(rawCode, bc.getCodeHash())) {
                bc.setUsedAt(Instant.now());
                backupCodeRepository.save(bc);
                return true;
            }
        }
        return false;
    }
}
