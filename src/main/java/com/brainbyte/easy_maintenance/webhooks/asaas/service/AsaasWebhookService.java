package com.brainbyte.easy_maintenance.webhooks.asaas.service;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookEvent;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.enums.WebhookEventStatus;
import com.brainbyte.easy_maintenance.webhooks.commons.service.WebhookEventService;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.brainbyte.easy_maintenance.commons.helper.DateUtils.parseEventDate;

@Slf4j
@Service
public class AsaasWebhookService {

    private final WebhookEventService webhookEventService;
    private final ObjectMapper objectMapper;
    private final Map<String, AsaasWebhookStrategy> strategies;

    public AsaasWebhookService(WebhookEventService webhookEventService, ObjectMapper objectMapper, List<AsaasWebhookStrategy> strategyList) {
        this.webhookEventService = webhookEventService;
        this.objectMapper = objectMapper;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AsaasWebhookStrategy::getEventType, s -> s));
    }

    @Async
    @Transactional
    public void processEvent(AsaasDTO.WebhookCheckoutEvent event) {
        if (event == null) {
            log.warn("[AsaasWebhook] Evento nulo recebido — ignorado.");
            return;
        }

        log.info("[AsaasWebhook] Processando evento {}/{}", event.id(), event.event());

        // Verificação prévia de duplicidade (otimização — evita alocar o payload)
        if (webhookEventService.findByProviderEventId(event.id()).isPresent()) {
            log.info("[AsaasWebhook] Evento {} já processado anteriormente. Ignorando.", event.id());
            return;
        }

        var strategy = strategies.get(event.event());
        String rawPayload = serializeWebhookEvent(event);

        WebhookEvent webhookEvent;
        try {
            // INSERT atômico: a constraint UNIQUE garante idempotência mesmo em race condition
            webhookEvent = webhookEventService.save(getWebhookEvent(event, rawPayload));
        } catch (DataIntegrityViolationException e) {
            log.info("[AsaasWebhook] Evento {} duplicado detectado via constraint (race condition). Ignorando.", event.id());
            return;
        }

        try {
            webhookEvent.setStatus(WebhookEventStatus.PROCESSING);
            webhookEventService.save(webhookEvent);

            if (strategy != null) {
                strategy.handle(event);
            } else {
                log.info("[AsaasWebhook] Tipo de evento {} sem handler registrado.", event.event());
            }

            webhookEvent.setStatus(WebhookEventStatus.PROCESSED);
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventService.save(webhookEvent);
            log.info("[AsaasWebhook] Evento {} processado com sucesso.", event.id());

        } catch (Exception e) {
            log.error("[AsaasWebhook] Erro ao processar evento {}: {}", event.id(), e.getMessage(), e);
            webhookEvent.setStatus(WebhookEventStatus.ERROR);
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventService.save(webhookEvent);
            // Não propagar: método é @Async — exceção seria perdida silenciosamente de qualquer forma.
            // O status ERROR no banco é o registro permanente do problema.
        }
    }

    private WebhookEvent getWebhookEvent(AsaasDTO.WebhookCheckoutEvent event, String rawPayload) {
        return WebhookEvent.builder()
                .providerEventId(event.id())
                .eventType(event.event())
                .eventCreatedAt(parseEventDate(event.dateCreated()))
                .payload(rawPayload)
                .status(WebhookEventStatus.RECEIVED)
                .build();
    }

    private String serializeWebhookEvent(AsaasDTO.WebhookCheckoutEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Error serializing asaas event payload", e);
            return "Serialization error";
        }
    }

}
