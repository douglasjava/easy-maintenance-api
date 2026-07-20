package com.brainbyte.easy_maintenance.webhooks.whatsapp.security;

import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.util.HexFormat;

/**
 * Valida o header {@code X-Hub-Signature-256} da Meta (HMAC-SHA256 do corpo bruto da requisição
 * usando o App Secret). Diferente de {@code AsaasWebhookController}, que nunca compara nenhum
 * header do request recebido — aqui a assinatura é recalculada e comparada em constant-time
 * (MessageDigest.isEqual) contra a recebida, para evitar timing attack e forjamento de eventos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppSignatureValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final WhatsAppProperties whatsAppProperties;

    public boolean isValid(String rawBody, String signatureHeader) {
        String appSecret = whatsAppProperties.appSecret();
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("[WhatsAppWebhook] WHATSAPP_APP_SECRET não configurado — requisição rejeitada.");
            return false;
        }

        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String receivedHex = signatureHeader.substring(SIGNATURE_PREFIX.length());
        String expectedHex = computeHmacHex(rawBody == null ? "" : rawBody, appSecret);
        if (expectedHex == null) {
            return false;
        }

        return MessageDigest.isEqual(
                expectedHex.getBytes(StandardCharsets.UTF_8),
                receivedHex.getBytes(StandardCharsets.UTF_8));
    }

    private String computeHmacHex(String rawBody, String appSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            // Nunca logar a causa completa aqui: em teoria não deveria expor segredo, mas
            // evita-se stacktrace de uma exceção que encapsule o SecretKeySpec por segurança.
            log.error("[WhatsAppWebhook] Falha ao calcular HMAC da assinatura recebida.");
            return null;
        }
    }
}
