package com.brainbyte.easy_maintenance.org_users.application.service;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class TotpService {

    private static final String ISSUER = "Easy Maintenance";
    private static final int BACKUP_CODE_COUNT = 8;

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String buildQrCodeDataUri(String userEmail, String secret) {
        QrData data = new QrData.Builder()
                .label(userEmail)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            QrGenerator generator = new ZxingPngQrGenerator();
            byte[] imageBytes = generator.generate(data);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (QrGenerationException e) {
            log.warn("QR code generation failed for user {}: {}", userEmail, e.getMessage());
            return null;
        }
    }

    public String buildOtpAuthUri(String userEmail, String secret) {
        QrData data = new QrData.Builder()
                .label(userEmail)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        return data.getUri();
    }

    public boolean verifyCode(String secret, String code) {
        if (code == null || code.isBlank()) return false;
        return codeVerifier.isValidCode(secret, code.trim());
    }

    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            byte[] bytes = new byte[5];
            secureRandom.nextBytes(bytes);
            String hex = String.format("%010x", new java.math.BigInteger(1, bytes));
            codes.add(hex.substring(0, 5) + "-" + hex.substring(5));
        }
        return codes;
    }

    public String hashBackupCode(String rawCode) {
        return bcrypt.encode(rawCode.trim().toLowerCase());
    }

    public boolean verifyBackupCode(String rawCode, String hash) {
        return bcrypt.matches(rawCode.trim().toLowerCase(), hash);
    }
}
