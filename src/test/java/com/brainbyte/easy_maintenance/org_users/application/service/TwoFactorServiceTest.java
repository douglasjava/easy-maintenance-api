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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock
    private TotpService totpService;
    @Mock
    private UserTotpSettingsRepository totpSettingsRepository;
    @Mock
    private UserBackupCodeRepository backupCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CriticalEmailDispatchService criticalEmailDispatchService;
    @Mock
    private EmailTemplateHelper emailTemplateHelper;

    @InjectMocks
    private TwoFactorService twoFactorService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(twoFactorService, "frontendBaseUrl", "http://localhost:3000");
    }

    // ─── getStatus ───────────────────────────────────────────────────────────

    @Test
    void getStatus_shouldReturnDisabled_whenNoSettingsExist() {
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        UserDTO.TwoFactorStatusResponse status = twoFactorService.getStatus(1L);

        assertThat(status.enabled()).isFalse();
        assertThat(status.backupCodesRemaining()).isZero();
    }

    @Test
    void getStatus_shouldReturnEnabled_withBackupCodeCount() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        List<UserBackupCode> unusedCodes = List.of(
                UserBackupCode.builder().userId(1L).codeHash("h1").build(),
                UserBackupCode.builder().userId(1L).codeHash("h2").build()
        );
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(unusedCodes);

        UserDTO.TwoFactorStatusResponse status = twoFactorService.getStatus(1L);

        assertThat(status.enabled()).isTrue();
        assertThat(status.backupCodesRemaining()).isEqualTo(2);
    }

    @Test
    void getStatus_shouldReturnZeroBackupCodes_whenDisabled() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        UserDTO.TwoFactorStatusResponse status = twoFactorService.getStatus(1L);

        assertThat(status.enabled()).isFalse();
        assertThat(status.backupCodesRemaining()).isZero();
        verifyNoInteractions(backupCodeRepository);
    }

    // ─── initiateSetup ───────────────────────────────────────────────────────

    @Test
    void initiateSetup_shouldThrowConflict_whenAlreadyEnabled() {
        User user = User.builder().id(1L).email("user@test.com").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(totpSettingsRepository.existsByUserIdAndEnabledTrue(1L)).thenReturn(true);

        assertThatThrownBy(() -> twoFactorService.initiateSetup(1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void initiateSetup_shouldThrowNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.initiateSetup(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void initiateSetup_shouldReturnSetupResponse_withQrAndUri() {
        User user = User.builder().id(1L).email("user@test.com").build();
        when(totpSettingsRepository.existsByUserIdAndEnabledTrue(1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(totpService.generateSecret()).thenReturn("MYSECRET32BASE32");
        when(totpService.buildQrCodeDataUri("user@test.com", "MYSECRET32BASE32"))
                .thenReturn("data:image/png;base64,ABC");
        when(totpService.buildOtpAuthUri("user@test.com", "MYSECRET32BASE32"))
                .thenReturn("otpauth://totp/user@test.com?secret=MYSECRET32BASE32");
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserDTO.TwoFactorSetupResponse resp = twoFactorService.initiateSetup(1L);

        assertThat(resp.secret()).isEqualTo("MYSECRET32BASE32");
        assertThat(resp.qrCodeDataUri()).isEqualTo("data:image/png;base64,ABC");
        assertThat(resp.otpAuthUri()).contains("otpauth://totp/");

        ArgumentCaptor<UserTotpSettings> captor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getTotpSecret()).isEqualTo("MYSECRET32BASE32");
    }

    @Test
    void initiateSetup_shouldUpdateExistingSettings_whenPendingSetupExists() {
        User user = User.builder().id(1L).email("user@test.com").build();
        UserTotpSettings existing = UserTotpSettings.builder()
                .userId(1L).totpSecret("OLDSECRET").enabled(false).build();
        when(totpSettingsRepository.existsByUserIdAndEnabledTrue(1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(totpService.generateSecret()).thenReturn("NEWSECRET32BASE3");
        when(totpService.buildQrCodeDataUri(any(), any())).thenReturn("data:image/png;base64,XYZ");
        when(totpService.buildOtpAuthUri(any(), any())).thenReturn("otpauth://totp/x");
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        twoFactorService.initiateSetup(1L);

        ArgumentCaptor<UserTotpSettings> captor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().getTotpSecret()).isEqualTo("NEWSECRET32BASE3");
    }

    // ─── confirmSetup ────────────────────────────────────────────────────────

    @Test
    void confirmSetup_shouldThrowRuleException_whenNoSettingsFound() {
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.confirmSetup(1L, "123456"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("setup");
    }

    @Test
    void confirmSetup_shouldThrowConflict_whenAlreadyEnabled() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> twoFactorService.confirmSetup(1L, "123456"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmSetup_shouldThrowRuleException_whenCodeInvalid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.confirmSetup(1L, "000000"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void confirmSetup_shouldEnable2faAndReturnBackupCodes_whenCodeValid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        List<String> rawCodes = List.of("aaa11-bbb22", "ccc33-ddd44");
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "654321")).thenReturn(true);
        when(totpService.generateBackupCodes()).thenReturn(rawCodes);
        when(totpService.hashBackupCode(anyString())).thenReturn("$2a$hash");
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(backupCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        UserDTO.TwoFactorConfirmResponse resp = twoFactorService.confirmSetup(1L, "654321");

        assertThat(resp.backupCodes()).containsExactlyElementsOf(rawCodes);
        ArgumentCaptor<UserTotpSettings> captor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isTrue();
    }

    // ─── disable ─────────────────────────────────────────────────────────────

    @Test
    void disable_shouldThrowRuleException_whenNoSettingsFound() {
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.disable(1L, "123456"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void disable_shouldThrowRuleException_whenNotEnabled() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> twoFactorService.disable(1L, "123456"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void disable_shouldThrowRuleException_whenBothTotpAndBackupCodeInvalid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);
        when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> twoFactorService.disable(1L, "000000"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Código inválido");
    }

    @Test
    void disable_shouldDisable2fa_whenTotpCodeValid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "654321")).thenReturn(true);
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        twoFactorService.disable(1L, "654321");

        ArgumentCaptor<UserTotpSettings> captor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        verify(backupCodeRepository).deleteByUserId(1L);
    }

    @Test
    void disable_shouldDisable2fa_whenBackupCodeValid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        UserBackupCode bc = UserBackupCode.builder()
                .userId(1L).codeHash("$2a$hash").build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "aaa11-bbb22")).thenReturn(false);
        when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(bc));
        when(totpService.verifyBackupCode("aaa11-bbb22", "$2a$hash")).thenReturn(true);
        when(backupCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        twoFactorService.disable(1L, "aaa11-bbb22");

        verify(backupCodeRepository).save(argThat(code -> code.getUsedAt() != null));
        ArgumentCaptor<UserTotpSettings> captor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    // ─── verifyForLogin ──────────────────────────────────────────────────────

    @Test
    void verifyForLogin_shouldReturnTrue_whenNoSettingsExist() {
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThat(twoFactorService.verifyForLogin(1L, "000000")).isTrue();
    }

    @Test
    void verifyForLogin_shouldReturnTrue_when2faNotEnabled() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThat(twoFactorService.verifyForLogin(1L, "000000")).isTrue();
    }

    @Test
    void verifyForLogin_shouldReturnTrue_whenTotpCodeValid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "654321")).thenReturn(true);

        assertThat(twoFactorService.verifyForLogin(1L, "654321")).isTrue();
    }

    @Test
    void verifyForLogin_shouldReturnFalse_whenBothTotpAndBackupCodeInvalid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);
        when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of());

        assertThat(twoFactorService.verifyForLogin(1L, "000000")).isFalse();
    }

    @Test
    void verifyForLogin_shouldReturnTrue_andConsumeBackupCode_whenBackupCodeMatches() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        UserBackupCode bc = UserBackupCode.builder()
                .userId(1L).codeHash("$2a$hash").build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "aaa11-bbb22")).thenReturn(false);
        when(backupCodeRepository.findByUserIdAndUsedAtIsNull(1L)).thenReturn(List.of(bc));
        when(totpService.verifyBackupCode("aaa11-bbb22", "$2a$hash")).thenReturn(true);
        when(backupCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean result = twoFactorService.verifyForLogin(1L, "aaa11-bbb22");

        assertThat(result).isTrue();
        verify(backupCodeRepository).save(argThat(code -> code.getUsedAt() != null));
    }

    // ─── isEnabled ───────────────────────────────────────────────────────────

    @Test
    void isEnabled_shouldDelegateToRepository() {
        when(totpSettingsRepository.existsByUserIdAndEnabledTrue(42L)).thenReturn(true);

        assertThat(twoFactorService.isEnabled(42L)).isTrue();
        verify(totpSettingsRepository).existsByUserIdAndEnabledTrue(42L);
    }

    // ─── regenerateBackupCodes ───────────────────────────────────────────────

    @Test
    void regenerateBackupCodes_shouldThrowRuleException_whenNotConfigured() {
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.regenerateBackupCodes(1L, "123456"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void regenerateBackupCodes_shouldThrowRuleException_whenNotEnabled() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> twoFactorService.regenerateBackupCodes(1L, "123456"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void regenerateBackupCodes_shouldThrowRuleException_whenTotpCodeInvalid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "000000")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.regenerateBackupCodes(1L, "000000"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void regenerateBackupCodes_shouldDeleteOldAndReturnNewCodes_whenCodeValid() {
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        List<String> newCodes = List.of("xxx11-yyy22", "zzz33-www44");
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpService.verifyCode("SECRET", "654321")).thenReturn(true);
        when(totpService.generateBackupCodes()).thenReturn(newCodes);
        when(totpService.hashBackupCode(anyString())).thenReturn("$2a$hash");
        when(backupCodeRepository.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        List<String> result = twoFactorService.regenerateBackupCodes(1L, "654321");

        assertThat(result).containsExactlyElementsOf(newCodes);
        verify(backupCodeRepository).deleteByUserId(1L);
    }

    // ─── requestEmailRecovery ────────────────────────────────────────────────

    @Test
    void requestEmailRecovery_shouldDoNothing_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        twoFactorService.requestEmailRecovery("ghost@test.com");

        verifyNoInteractions(totpSettingsRepository, criticalEmailDispatchService);
    }

    @Test
    void requestEmailRecovery_shouldDoNothing_whenNo2faSettings() {
        User user = User.builder().id(1L).email("user@test.com").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        twoFactorService.requestEmailRecovery("user@test.com");

        verifyNoInteractions(criticalEmailDispatchService);
    }

    @Test
    void requestEmailRecovery_shouldDoNothing_when2faDisabled() {
        User user = User.builder().id(1L).email("user@test.com").name("Test User").build();
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(false).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        twoFactorService.requestEmailRecovery("user@test.com");

        verifyNoInteractions(criticalEmailDispatchService);
    }

    @Test
    void requestEmailRecovery_shouldSaveTokenAndSendEmail_when2faEnabled() {
        User user = User.builder().id(1L).email("user@test.com").name("Test User").build();
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(emailTemplateHelper.generateTwoFactorRecoveryHtml(anyString(), anyString())).thenReturn("<html/>");

        twoFactorService.requestEmailRecovery("user@test.com");

        ArgumentCaptor<UserTotpSettings> settingsCaptor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(settingsCaptor.capture());
        assertThat(settingsCaptor.getValue().getRecoveryToken()).isNotBlank();
        assertThat(settingsCaptor.getValue().getRecoveryExpiresAt()).isAfter(Instant.now());

        verify(criticalEmailDispatchService).send(
                eq("user@test.com"), eq("Test User"), isNull(),
                eq(NotificationEventType.TWO_FACTOR_RECOVERY),
                anyString(), eq("<html/>"), eq(false));
    }

    // ─── applyEmailRecovery ──────────────────────────────────────────────────

    @Test
    void applyEmailRecovery_shouldThrowRuleException_whenUserNotFound() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.applyEmailRecovery("ghost@test.com", "any-token"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void applyEmailRecovery_shouldThrowRuleException_whenNoSettings() {
        User user = User.builder().id(1L).email("user@test.com").build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> twoFactorService.applyEmailRecovery("user@test.com", "any-token"))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void applyEmailRecovery_shouldThrowRuleException_whenTokenMismatch() {
        User user = User.builder().id(1L).email("user@test.com").build();
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).recoveryToken("correct-token")
                .recoveryExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> twoFactorService.applyEmailRecovery("user@test.com", "wrong-token"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void applyEmailRecovery_shouldThrowRuleException_whenTokenExpired() {
        User user = User.builder().id(1L).email("user@test.com").build();
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).recoveryToken("valid-token")
                .recoveryExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES)).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> twoFactorService.applyEmailRecovery("user@test.com", "valid-token"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("expirado");
    }

    @Test
    void applyEmailRecovery_shouldDisable2faAndClearToken_whenTokenValid() {
        User user = User.builder().id(1L).email("user@test.com").build();
        UserTotpSettings settings = UserTotpSettings.builder()
                .userId(1L).totpSecret("SECRET").enabled(true)
                .recoveryToken("valid-token")
                .recoveryExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)).build();
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(totpSettingsRepository.findByUserId(1L)).thenReturn(Optional.of(settings));
        when(totpSettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        twoFactorService.applyEmailRecovery("user@test.com", "valid-token");

        ArgumentCaptor<UserTotpSettings> captor = ArgumentCaptor.forClass(UserTotpSettings.class);
        verify(totpSettingsRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getRecoveryToken()).isNull();
        assertThat(captor.getValue().getRecoveryExpiresAt()).isNull();
        verify(backupCodeRepository).deleteByUserId(1L);
    }
}
