package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.ai.domain.AiMonthlyUsage;
import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiMonthlyUsageRepository;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.org_users.application.dto.LgpdDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.PasswordResetTokenRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserTotpSettingsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LgpdServiceTest {

    @Mock UserRepository userRepository;
    @Mock BillingAccountRepository billingAccountRepository;
    @Mock AiMonthlyUsageRepository aiMonthlyUsageRepository;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock UserTotpSettingsRepository userTotpSettingsRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;

    @InjectMocks LgpdService lgpdService;

    private User buildUser(Long id, String email) {
        var org = UserOrganization.builder().organizationCode("org-abc").build();
        return User.builder()
                .id(id)
                .name("Douglas Dias")
                .email(email)
                .passwordHash("$2a$10$hashedpassword")
                .status(Status.ACTIVE)
                .organizations(new java.util.ArrayList<>(List.of(org)))
                .createdAt(Instant.now())
                .build();
    }

    // ─── exportData ───────────────────────────────────────────────────────────

    @Test
    void exportData_returnsAllUserData() {
        User user = buildUser(1L, "user@test.com");
        when(userRepository.findByIdWithOrganization(1L)).thenReturn(Optional.of(user));
        when(billingAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());

        AiMonthlyUsage usage = AiMonthlyUsage.builder().usageMonth("2026-05").creditsUsed(42).build();
        when(aiMonthlyUsageRepository.findAllByUserId(1L)).thenReturn(List.of(usage));

        LgpdDTO.DataExportResponse result = lgpdService.exportData(1L);

        assertThat(result.user().email()).isEqualTo("user@test.com");
        assertThat(result.user().name()).isEqualTo("Douglas Dias");
        assertThat(result.organizations()).containsExactly("org-abc");
        assertThat(result.aiUsage()).hasSize(1);
        assertThat(result.aiUsage().get(0).creditsUsed()).isEqualTo(42);
        assertThat(result.billing()).isNull();

        verify(auditService).log(eq("User"), eq("1"), eq(AuditAction.OTHER), anyString());
    }

    @Test
    void exportData_throwsNotFound_whenUserDoesNotExist() {
        when(userRepository.findByIdWithOrganization(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lgpdService.exportData(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── anonymizeAccount ─────────────────────────────────────────────────────

    @Test
    void anonymizeAccount_anonymizesAndDeletesUser() {
        User user = buildUser(1L, "user@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-pass", user.getPasswordHash())).thenReturn(true);
        when(billingAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(userTotpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        lgpdService.anonymizeAccount(1L, "correct-pass");

        assertThat(user.getName()).isEqualTo("Usuário Removido");
        assertThat(user.getEmail()).startsWith("anon-");
        assertThat(user.getEmail()).endsWith("@removed.invalid");
        assertThat(user.getPasswordHash()).isEmpty();
        assertThat(user.getStatus()).isEqualTo(Status.INACTIVE);

        verify(passwordResetTokenRepository).deleteAllByUserId(1L);
        verify(userRepository).delete(user);
        verify(auditService).log(eq("User"), eq("1"), eq(AuditAction.DELETE), anyString());
    }

    @Test
    void anonymizeAccount_clearsBillingAccountPii() {
        User user = buildUser(1L, "user@test.com");
        BillingAccount billing = BillingAccount.builder()
                .name("Douglas Dias")
                .billingEmail("user@test.com")
                .doc("123.456.789-00")
                .phone("11999999999")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pass", user.getPasswordHash())).thenReturn(true);
        when(billingAccountRepository.findByUserId(1L)).thenReturn(Optional.of(billing));
        when(userTotpSettingsRepository.findByUserId(1L)).thenReturn(Optional.empty());

        lgpdService.anonymizeAccount(1L, "pass");

        assertThat(billing.getName()).isNull();
        assertThat(billing.getBillingEmail()).isNull();
        assertThat(billing.getDoc()).isNull();
        assertThat(billing.getPhone()).isNull();
        verify(billingAccountRepository).save(billing);
    }

    @Test
    void anonymizeAccount_throwsNotAuthorized_whenPasswordWrong() {
        User user = buildUser(1L, "user@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-pass", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> lgpdService.anonymizeAccount(1L, "wrong-pass"))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessageContaining("Senha incorreta");

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void anonymizeAccount_throwsNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lgpdService.anonymizeAccount(99L, "any-pass"))
                .isInstanceOf(NotFoundException.class);
    }
}
