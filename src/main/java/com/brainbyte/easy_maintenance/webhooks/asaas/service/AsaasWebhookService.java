package com.brainbyte.easy_maintenance.webhooks.asaas.service;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookEvent;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.enums.WebhookEventStatus;
import com.brainbyte.easy_maintenance.webhooks.commons.service.WebhookEventService;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AsaasWebhookStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Transactional
    public void processEvent(AsaasDTO.WebhookCheckoutEvent event) {
        if (event == null) {
            log.warn("Asaas webhook received null event");
            return;
        }

        log.info("[AsaasWebhook] Event {}/{} started.", event.id(), event.event());

        if (webhookEventService.findByProviderEventId(event.id()).isPresent()) {
            log.info("[AsaasWebhook] Event {} already processed. Skipping.", event.id());
            return;
        }

        var strategy = strategies.get(event.event());
        String rawPayload = serializeWebhookEvent(event);

        var webhookEvent = getWebhookEvent(event, rawPayload);

        webhookEvent = webhookEventService.save(webhookEvent);

        try {
            webhookEvent.setStatus(WebhookEventStatus.PROCESSING);
            webhookEventService.save(webhookEvent);

            if (strategy != null) {
                strategy.handle(event);
            } else {
                log.info("[AsaasWebhook] Event type {} not handled", event.event());
            }

            webhookEvent.setStatus(WebhookEventStatus.PROCESSED);
            webhookEvent.setProcessedAt(Instant.now());
            webhookEventService.save(webhookEvent);
            log.info("[AsaasWebhook] Event {} processed successfully", event.id());

        } catch (Exception e) {
            log.error("[AsaasWebhook] Error processing event {}: {}", event.id(), e.getMessage(), e);
            webhookEvent.setStatus(WebhookEventStatus.ERROR);
            webhookEvent.setErrorMessage(e.getMessage());
            webhookEventService.save(webhookEvent);
            throw e;
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

    private Instant parseEventDate(String dateStr) {
        try {
            return OffsetDateTime.parse(dateStr).toInstant();
        } catch (Exception e) {
            return Instant.now();
        }
    }

}
