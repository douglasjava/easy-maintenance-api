package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.InternalErrorException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.mail.MailerSendService;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.PasswordResetToken;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.PasswordResetTokenRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final UsersService usersService;
    private final MailerSendService mailerSendService;
    private final EmailTemplateHelper emailTemplateHelper;

    @Value("${frontend.reset-password-url}")
    private String resetPasswordUrl;

    @Transactional
    public void forgotPassword(UserDTO.ForgotPasswordRequest request, String requestedIp, String userAgent) {
        log.info("Solicitação de recuperação de senha para o e-mail: {}", request.email());

        userRepository.findByEmail(request.email()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            String tokenHash = hashToken(token);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .userId(user.getId())
                    .tokenHash(tokenHash)
                    .requestedIp(requestedIp)
                    .requestedUserAgent(userAgent)
                    .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                    .build();

            tokenRepository.save(resetToken);

            String resetLink = resetPasswordUrl + "?token=" + token;
            String htmlContent = emailTemplateHelper.generatePasswordResetHtml(user.getName(), resetLink);

            mailerSendService.sendEmail(
                    user.getEmail(),
                    user.getName(),
                    "Recuperação de Senha - Easy Maintenance",
                    "Clique no link para redefinir sua senha: " + resetLink,
                    htmlContent
            );
        });
    }

    @Transactional
    public void resetPassword(UserDTO.ResetPasswordRequest request) {
        log.info("Processando reset de senha com token");

        String tokenHash = hashToken(request.token());

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new NotFoundException("Token inválido ou não encontrado"));

        validateResetToken(resetToken);

        userRepository.findById(resetToken.getUserId())
                .ifPresentOrElse(
                        user -> usersService.resetPassword(user, request.newPassword()),
                        () -> { throw new NotFoundException("Usuário não encontrado"); }
                );

        resetToken.setUsedAt(Instant.now());

    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalErrorException("Erro ao gerar hash do token", e);
        }
    }

    private void validateResetToken(PasswordResetToken token) {
        if (token.getUsedAt() != null) {
            throw new ConflictException("Este link já foi utilizado");
        }

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("Este link expirou");
        }
    }


}
