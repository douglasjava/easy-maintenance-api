package com.brainbyte.easy_maintenance.org_users.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService();
    }

    // ─── generateSecret ──────────────────────────────────────────────────────

    @Test
    void generateSecret_shouldReturnNonBlankBase32String() {
        String secret = totpService.generateSecret();
        assertThat(secret).isNotBlank();
        // Base32 alphabet: A-Z and 2-7, plus optional padding '='
        assertThat(secret).matches("[A-Z2-7=]+");
    }

    @Test
    void generateSecret_shouldProduceDifferentSecretsEachCall() {
        String s1 = totpService.generateSecret();
        String s2 = totpService.generateSecret();
        assertThat(s1).isNotEqualTo(s2);
    }

    // ─── buildOtpAuthUri ─────────────────────────────────────────────────────

    @Test
    void buildOtpAuthUri_shouldReturnValidOtpauthUri() {
        String secret = totpService.generateSecret();
        String uri = totpService.buildOtpAuthUri("user@example.com", secret);

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("issuer=Easy");
        assertThat(uri).contains("secret=" + secret);
    }

    // ─── buildQrCodeDataUri ──────────────────────────────────────────────────

    @Test
    void buildQrCodeDataUri_shouldReturnBase64DataUri() {
        String secret = totpService.generateSecret();
        String dataUri = totpService.buildQrCodeDataUri("user@example.com", secret);

        assertThat(dataUri).isNotNull();
        assertThat(dataUri).startsWith("data:image/png;base64,");
        assertThat(dataUri.length()).isGreaterThan(100);
    }

    // ─── verifyCode ──────────────────────────────────────────────────────────

    @Test
    void verifyCode_shouldReturnFalse_forNullCode() {
        String secret = totpService.generateSecret();
        assertThat(totpService.verifyCode(secret, null)).isFalse();
    }

    @Test
    void verifyCode_shouldReturnFalse_forBlankCode() {
        String secret = totpService.generateSecret();
        assertThat(totpService.verifyCode(secret, "   ")).isFalse();
    }

    @Test
    void verifyCode_shouldReturnFalse_forWrongCode() {
        String secret = totpService.generateSecret();
        assertThat(totpService.verifyCode(secret, "000000")).isFalse();
    }

    // ─── generateBackupCodes ─────────────────────────────────────────────────

    @Test
    void generateBackupCodes_shouldReturn8Codes() {
        List<String> codes = totpService.generateBackupCodes();
        assertThat(codes).hasSize(8);
    }

    @Test
    void generateBackupCodes_shouldFollowXxxxxDashXxxxxPattern() {
        Pattern pattern = Pattern.compile("[0-9a-f]{5}-[0-9a-f]{5}");
        List<String> codes = totpService.generateBackupCodes();
        for (String code : codes) {
            assertThat(code).matches(pattern);
        }
    }

    @Test
    void generateBackupCodes_shouldProduceUniqueCodes() {
        List<String> codes = totpService.generateBackupCodes();
        long distinct = codes.stream().distinct().count();
        assertThat(distinct).isEqualTo(8);
    }

    @Test
    void generateBackupCodes_shouldProduceDifferentSetsAcrossCalls() {
        List<String> first = totpService.generateBackupCodes();
        List<String> second = totpService.generateBackupCodes();
        assertThat(first).isNotEqualTo(second);
    }

    // ─── hashBackupCode / verifyBackupCode ───────────────────────────────────

    @Test
    void hashBackupCode_shouldProduceBcryptHash() {
        String raw = "abc12-def34";
        String hash = totpService.hashBackupCode(raw);
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void verifyBackupCode_shouldReturnTrue_forCorrectRawCode() {
        String raw = "abc12-def34";
        String hash = totpService.hashBackupCode(raw);
        assertThat(totpService.verifyBackupCode(raw, hash)).isTrue();
    }

    @Test
    void verifyBackupCode_shouldReturnFalse_forWrongRawCode() {
        String raw = "abc12-def34";
        String hash = totpService.hashBackupCode(raw);
        assertThat(totpService.verifyBackupCode("wrong-xxxxx", hash)).isFalse();
    }

    @Test
    void verifyBackupCode_shouldBeCaseInsensitive() {
        String raw = "ABC12-DEF34";
        String hash = totpService.hashBackupCode(raw);
        assertThat(totpService.verifyBackupCode("abc12-def34", hash)).isTrue();
    }

    @Test
    void verifyBackupCode_shouldIgnoreSurroundingWhitespace() {
        String raw = "  abc12-def34  ";
        String hash = totpService.hashBackupCode(raw);
        assertThat(totpService.verifyBackupCode("abc12-def34", hash)).isTrue();
    }
}
