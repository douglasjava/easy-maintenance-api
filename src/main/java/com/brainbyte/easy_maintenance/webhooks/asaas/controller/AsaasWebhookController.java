package com.brainbyte.easy_maintenance.webhooks.asaas.controller;

import com.brainbyte.easy_maintenance.commons.helper.HttpUtils;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.webhooks.asaas.service.AsaasWebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/public/webhooks/asaas")
public class AsaasWebhookController {

    private final AsaasWebhookService webhookService;
    private final AsaasProperties asaasProperties;

    @PostMapping
    public ResponseEntity<String> handleEvent(
            @RequestHeader(value = "asaas-access-token", required = false) String token,
            @RequestBody AsaasDTO.WebhookCheckoutEvent payload,
            HttpServletRequest request) {

        String clientIp = HttpUtils.getClientIp(request);
        String webhookToken = asaasProperties.webhookToken();

        if (webhookToken == null || webhookToken.isBlank()) {
            log.warn("[AsaasWebhook] Token não configurado — requisição rejeitada. IP={}", clientIp);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("webhook token not configured");
        }

        if (token == null || token.isBlank() || !webhookToken.equals(token)) {
            log.warn("[AsaasWebhook] Token inválido rejeitado. IP={}, eventId={}", clientIp,
                    payload != null ? payload.id() : "null");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid token");
        }

        // Responde 200 imediatamente para evitar timeout/reenvio pelo Asaas.
        // O processamento ocorre de forma assíncrona.
        webhookService.processEvent(payload);
        return ResponseEntity.ok("ok");
    }
}
