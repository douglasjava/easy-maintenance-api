package com.brainbyte.easy_maintenance.webhooks.commons.service;

import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AsaasWebhookStrategy;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookDlqEntry;
import com.brainbyte.easy_maintenance.webhooks.commons.repository.WebhookDlqRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WebhookDlqService {

    static final String METRIC_NAME = "billing.webhook.dlq.count";

    private final WebhookDlqRepository repository;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Map<String, AsaasWebhookStrategy> strategies;

    public WebhookDlqService(WebhookDlqRepository repository,
                              MeterRegistry meterRegistry,
                              ObjectMapper objectMapper,
                              List<AsaasWebhookStrategy> strategyList) {
        this.repository = repository;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AsaasWebhookStrategy::getEventType, s -> s));
    }

    @Transactional
    public void enqueue(String providerEventId, String eventType, String payload, String errorMessage) {
        var now = Instant.now();
        var existing = repository.findByProviderEventId(providerEventId);

        if (existing.isPresent()) {
            var entry = existing.get();
            entry.setAttempts(entry.getAttempts() + 1);
            entry.setLastFailedAt(now);
            entry.setErrorMessage(errorMessage);
            repository.save(entry);
            log.warn("[WebhookDlq] Updated DLQ entry: providerEventId={}, attempts={}", providerEventId, entry.getAttempts());
        } else {
            repository.save(WebhookDlqEntry.builder()
                    .providerEventId(providerEventId)
                    .eventType(eventType)
                    .payload(payload)
                    .errorMessage(errorMessage)
                    .attempts(1)
                    .firstFailedAt(now)
                    .lastFailedAt(now)
                    .build());
            meterRegistry.counter(METRIC_NAME, "event_type", eventType).increment();
            log.warn("[WebhookDlq] New DLQ entry: providerEventId={}, eventType={}", providerEventId, eventType);
        }
    }

    @Transactional
    public void replay(Long dlqId) {
        var entry = repository.findById(dlqId)
                .orElseThrow(() -> new NotFoundException("DLQ entry not found: " + dlqId));

        if (entry.getReplayedAt() != null) {
            throw new RuleException("Evento já foi reprocessado em " + entry.getReplayedAt());
        }

        AsaasDTO.WebhookCheckoutEvent event;
        try {
            event = objectMapper.readValue(entry.getPayload(), AsaasDTO.WebhookCheckoutEvent.class);
        } catch (Exception e) {
            throw new RuleException("Falha ao deserializar payload da DLQ: " + e.getMessage());
        }

        var strategy = strategies.get(entry.getEventType());
        if (strategy == null) {
            throw new RuleException("Nenhum handler registrado para eventType=" + entry.getEventType());
        }

        strategy.handle(event);

        entry.setReplayedAt(Instant.now());
        entry.setAttempts(entry.getAttempts() + 1);
        repository.save(entry);

        log.info("[WebhookDlq] Replay concluído: dlqId={}, eventType={}", dlqId, entry.getEventType());
    }

    @Transactional(readOnly = true)
    public Page<WebhookDlqEntry> listPending(Pageable pageable) {
        return repository.findByReplayedAtIsNull(pageable);
    }
}
