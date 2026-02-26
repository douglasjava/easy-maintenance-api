package com.brainbyte.easy_maintenance.webhooks.asaas.controller;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.webhooks.asaas.service.AsaasWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/public/webhooks/asaas")
public class AsaasWebhookController {

    private final AsaasWebhookService webhookService;
    private final AsaasProperties asaasProperties;

    @PostMapping
    public ResponseEntity<String> handleEvent(@RequestHeader(value = "asaas-access-token", required = false) String token,
            @RequestBody AsaasDTO.WebhookCheckoutEvent payload) {

        String webhookToken = asaasProperties.webhookToken();

        if (webhookToken == null || webhookToken.isBlank()) {
            log.warn("Asaas webhook token não configurado");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("webhook token not configured");
        }

        if (token == null || token.isBlank() || !webhookToken.equals(token)) {
            log.warn("Asaas webhook invalid token received");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("invalid token");
        }

        try {
            webhookService.processEvent(payload);
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Error processing Asaas webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }

    }
}
