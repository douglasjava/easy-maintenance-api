package com.brainbyte.easy_maintenance.webhooks.whatsapp.controller;

import com.brainbyte.easy_maintenance.commons.helper.HttpUtils;
import com.brainbyte.easy_maintenance.infrastructure.notification.properties.WhatsAppProperties;
import com.brainbyte.easy_maintenance.webhooks.whatsapp.security.WhatsAppSignatureValidator;
import com.brainbyte.easy_maintenance.webhooks.whatsapp.service.WhatsAppWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Handshake de verificação (GET) e recebimento de eventos (POST) da WhatsApp Cloud API (Meta),
 * TASK-128. Mesmo prefixo {@code public/webhooks/} do Asaas por consistência de
 * SecurityConfig/TenantFilter, mas com validação de assinatura real — ver
 * {@link WhatsAppSignatureValidator}, ausente no {@code AsaasWebhookController}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/public/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private static final String SUBSCRIBE_MODE = "subscribe";

    private final WhatsAppWebhookService webhookService;
    private final WhatsAppSignatureValidator signatureValidator;
    private final WhatsAppProperties whatsAppProperties;

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            HttpServletRequest request) {

        String expectedToken = whatsAppProperties.webhookVerifyToken();
        boolean tokenConfigured = expectedToken != null && !expectedToken.isBlank();

        if (tokenConfigured && SUBSCRIBE_MODE.equals(mode) && constantTimeEquals(expectedToken, verifyToken)) {
            log.info("[WhatsAppWebhook] Handshake de verificação bem-sucedido. IP={}", HttpUtils.getClientIp(request));
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(challenge);
        }

        log.warn("[WhatsAppWebhook] Handshake de verificação rejeitado. IP={}", HttpUtils.getClientIp(request));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            HttpServletRequest request) {

        if (!signatureValidator.isValid(rawBody, signature)) {
            log.warn("[WhatsAppWebhook] Assinatura ausente ou inválida — requisição rejeitada. IP={}",
                    HttpUtils.getClientIp(request));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // Ack imediato: processamento pesado roda assíncrono (WhatsAppWebhookService#processEvent),
        // a Meta reenvia o evento se não receber 200 a tempo.
        webhookService.processEvent(rawBody);

        return ResponseEntity.ok().build();
    }

    private boolean constantTimeEquals(String expected, String received) {
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8));
    }
}
