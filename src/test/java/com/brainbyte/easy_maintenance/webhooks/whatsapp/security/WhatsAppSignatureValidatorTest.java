package com.brainbyte.easy_maintenance.webhooks.whatsapp.security;

import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppSignatureValidatorTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final String BODY = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}";

    private WhatsAppSignatureValidator validatorWith(String appSecret) {
        WhatsAppProperties properties = new WhatsAppProperties(
                "https://graph.facebook.com/v21.0", "token", "phoneId", "wabaId",
                "template", "verify-token", appSecret);
        return new WhatsAppSignatureValidator(properties);
    }

    private String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void isValid_returnsTrue_whenSignatureMatchesBody() throws Exception {
        WhatsAppSignatureValidator validator = validatorWith(APP_SECRET);
        String signature = sign(BODY, APP_SECRET);

        assertThat(validator.isValid(BODY, signature)).isTrue();
    }

    @Test
    void isValid_returnsFalse_whenBodyWasTampered() throws Exception {
        WhatsAppSignatureValidator validator = validatorWith(APP_SECRET);
        String signature = sign(BODY, APP_SECRET);
        String tamperedBody = BODY.replace("whatsapp_business_account", "forged");

        assertThat(validator.isValid(tamperedBody, signature)).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignatureForgedWithWrongSecret() throws Exception {
        WhatsAppSignatureValidator validator = validatorWith(APP_SECRET);
        String forgedSignature = sign(BODY, "wrong-secret");

        assertThat(validator.isValid(BODY, forgedSignature)).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignatureHeaderMissing() {
        WhatsAppSignatureValidator validator = validatorWith(APP_SECRET);

        assertThat(validator.isValid(BODY, null)).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenSignatureHeaderMissingPrefix() throws Exception {
        WhatsAppSignatureValidator validator = validatorWith(APP_SECRET);
        String signature = sign(BODY, APP_SECRET).replace("sha256=", "");

        assertThat(validator.isValid(BODY, signature)).isFalse();
    }

    @Test
    void isValid_returnsFalse_whenAppSecretNotConfigured() throws Exception {
        WhatsAppSignatureValidator validator = validatorWith("");
        String signature = sign(BODY, APP_SECRET);

        assertThat(validator.isValid(BODY, signature)).isFalse();
    }
}
