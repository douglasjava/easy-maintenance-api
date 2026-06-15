package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.ai.infrastructure.persistence.AiMonthlyUsageRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotAuthorizedException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.org_users.application.dto.LgpdDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.PasswordResetTokenRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserTotpSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LgpdService {

    private final UserRepository userRepository;
    private final BillingAccountRepository billingAccountRepository;
    private final AiMonthlyUsageRepository aiMonthlyUsageRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserTotpSettingsRepository userTotpSettingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public LgpdDTO.DataExportResponse exportData(Long userId) {
        User user = userRepository.findByIdWithOrganization(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        var userInfo = new LgpdDTO.DataExportResponse.UserInfo(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getStatus() != null ? user.getStatus().name() : null,
                user.getCreatedAt()
        );

        var billingInfo = billingAccountRepository.findByUserId(userId).map(ba ->
                new LgpdDTO.DataExportResponse.BillingInfo(
                        ba.getName(),
                        ba.getBillingEmail(),
                        ba.getDoc(),
                        ba.getPhone(),
                        ba.getStreet(),
                        ba.getNumber(),
                        ba.getComplement(),
                        ba.getNeighborhood(),
                        ba.getCity(),
                        ba.getState(),
                        ba.getZipCode(),
                        ba.getCountry(),
                        ba.getPaymentMethod() != null ? ba.getPaymentMethod().name() : null
                )
        ).orElse(null);

        List<String> organizations = user.getOrganizations().stream()
                .map(uo -> uo.getOrganizationCode())
                .toList();

        List<LgpdDTO.DataExportResponse.AiUsageInfo> aiUsage = aiMonthlyUsageRepository
                .findAllByUserId(userId).stream()
                .map(u -> new LgpdDTO.DataExportResponse.AiUsageInfo(u.getUsageMonth(), u.getCreditsUsed()))
                .toList();

        auditService.log("User", userId.toString(), AuditAction.OTHER, "LGPD data export requested");

        return new LgpdDTO.DataExportResponse(userInfo, billingInfo, organizations, aiUsage, Instant.now().toString());
    }

    @Transactional
    public void anonymizeAccount(Long userId, String confirmPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

        if (!passwordEncoder.matches(confirmPassword, user.getPasswordHash())) {
            throw new NotAuthorizedException("Senha incorreta. Confirme sua senha para excluir a conta.");
        }

        log.info("[LGPD] Iniciando anonimização da conta userId={}", userId);

        // Anonimizar dados pessoais do usuário
        String anonEmail = "anon-" + sha256Prefix(user.getEmail()) + "@removed.invalid";
        user.setName("Usuário Removido");
        user.setEmail(anonEmail);
        user.setPasswordHash("");
        user.setStatus(Status.INACTIVE);
        userRepository.save(user);

        // Anonimizar dados de faturamento
        billingAccountRepository.findByUserId(userId).ifPresent(ba -> {
            ba.setName(null);
            ba.setBillingEmail(null);
            ba.setDoc(null);
            ba.setPhone(null);
            ba.setStreet(null);
            ba.setNumber(null);
            ba.setComplement(null);
            ba.setNeighborhood(null);
            ba.setCity(null);
            ba.setState(null);
            ba.setZipCode(null);
            billingAccountRepository.save(ba);
        });

        // Remover tokens de segurança
        passwordResetTokenRepository.deleteAllByUserId(userId);
        userTotpSettingsRepository.findByUserId(userId).ifPresent(userTotpSettingsRepository::delete);

        // Soft delete — triggers @SQLDelete
        userRepository.delete(user);

        auditService.log("User", userId.toString(), AuditAction.DELETE, "LGPD account anonymization completed");
        log.info("[LGPD] Anonimização concluída para userId={} → email={}", userId, anonEmail);
    }

    private static String sha256Prefix(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(Math.abs(input.hashCode())).substring(0, 8);
        }
    }
}
