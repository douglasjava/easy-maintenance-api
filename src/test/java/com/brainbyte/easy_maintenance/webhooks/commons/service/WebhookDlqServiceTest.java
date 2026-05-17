package com.brainbyte.easy_maintenance.webhooks.commons.service;

import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AsaasWebhookStrategy;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookDlqEntry;
import com.brainbyte.easy_maintenance.webhooks.commons.repository.WebhookDlqRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookDlqServiceTest {

    @Mock private WebhookDlqRepository repository;
    @Mock private AsaasWebhookStrategy strategy;

    @Spy
    private SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private WebhookDlqService service;
    private ObjectMapper objectMapper;

    private static final String EVENT_ID = "evt-001";
    private static final String EVENT_TYPE = "PAYMENT_REFUSED";
    private static final String PAYLOAD = "{\"id\":\"evt-001\",\"event\":\"PAYMENT_REFUSED\",\"dateCreated\":\"2026-05-16T10:00:00\",\"account\":null,\"checkout\":null,\"payment\":null,\"subscription\":null}";
    private static final String ERROR_MSG = "handler error";

    @BeforeEach
    void setUp() {
        when(strategy.getEventType()).thenReturn(EVENT_TYPE);
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new WebhookDlqService(repository, meterRegistry, objectMapper, List.of(strategy));
    }

    // -------------------------------------------------------------------------
    // enqueue() — new entry
    // -------------------------------------------------------------------------

    @Test
    void enqueue_newEntry_savesAndIncrementsMetric() {
        when(repository.findByProviderEventId(EVENT_ID)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.enqueue(EVENT_ID, EVENT_TYPE, PAYLOAD, ERROR_MSG);

        ArgumentCaptor<WebhookDlqEntry> captor = ArgumentCaptor.forClass(WebhookDlqEntry.class);
        verify(repository).save(captor.capture());
        WebhookDlqEntry saved = captor.getValue();

        assertThat(saved.getProviderEventId()).isEqualTo(EVENT_ID);
        assertThat(saved.getEventType()).isEqualTo(EVENT_TYPE);
        assertThat(saved.getPayload()).isEqualTo(PAYLOAD);
        assertThat(saved.getErrorMessage()).isEqualTo(ERROR_MSG);
        assertThat(saved.getAttempts()).isEqualTo(1);
        assertThat(saved.getFirstFailedAt()).isNotNull();
        assertThat(saved.getLastFailedAt()).isNotNull();
        assertThat(saved.getReplayedAt()).isNull();

        double count = meterRegistry.counter(WebhookDlqService.METRIC_NAME, "event_type", EVENT_TYPE).count();
        assertThat(count).isEqualTo(1.0);
    }

    // -------------------------------------------------------------------------
    // enqueue() — existing entry (duplicate failure)
    // -------------------------------------------------------------------------

    @Test
    void enqueue_existingEntry_incrementsAttemptsDoesNotDuplicateMetric() {
        WebhookDlqEntry existing = WebhookDlqEntry.builder()
                .id(1L)
                .providerEventId(EVENT_ID)
                .eventType(EVENT_TYPE)
                .payload(PAYLOAD)
                .attempts(1)
                .firstFailedAt(Instant.now().minusSeconds(60))
                .lastFailedAt(Instant.now().minusSeconds(60))
                .build();

        when(repository.findByProviderEventId(EVENT_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.enqueue(EVENT_ID, EVENT_TYPE, PAYLOAD, "second error");

        ArgumentCaptor<WebhookDlqEntry> captor = ArgumentCaptor.forClass(WebhookDlqEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(2);
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("second error");

        // Metric NOT incremented again (new-entry only)
        double count = meterRegistry.counter(WebhookDlqService.METRIC_NAME, "event_type", EVENT_TYPE).count();
        assertThat(count).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // replay() — success
    // -------------------------------------------------------------------------

    @Test
    void replay_validPendingEntry_callsHandlerAndSetsReplayedAt() {
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(1L)
                .providerEventId(EVENT_ID)
                .eventType(EVENT_TYPE)
                .payload(PAYLOAD)
                .attempts(1)
                .firstFailedAt(Instant.now())
                .lastFailedAt(Instant.now())
                .build();

        when(repository.findById(1L)).thenReturn(Optional.of(entry));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.replay(1L);

        verify(strategy).handle(any(AsaasDTO.WebhookCheckoutEvent.class));

        ArgumentCaptor<WebhookDlqEntry> captor = ArgumentCaptor.forClass(WebhookDlqEntry.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getReplayedAt()).isNotNull();
        assertThat(captor.getValue().getAttempts()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // replay() — already replayed
    // -------------------------------------------------------------------------

    @Test
    void replay_alreadyReplayed_throwsRuleException() {
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(2L)
                .providerEventId("evt-002")
                .eventType(EVENT_TYPE)
                .payload(PAYLOAD)
                .attempts(2)
                .firstFailedAt(Instant.now())
                .lastFailedAt(Instant.now())
                .replayedAt(Instant.now())
                .build();

        when(repository.findById(2L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.replay(2L))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("já foi reprocessado");

        verify(strategy, never()).handle(any());
    }

    // -------------------------------------------------------------------------
    // replay() — entry not found
    // -------------------------------------------------------------------------

    @Test
    void replay_entryNotFound_throwsNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.replay(99L))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // replay() — no handler
    // -------------------------------------------------------------------------

    @Test
    void replay_noRegisteredHandler_throwsRuleException() {
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(3L)
                .providerEventId("evt-003")
                .eventType("UNKNOWN_EVENT_TYPE")
                .payload(PAYLOAD)
                .attempts(1)
                .firstFailedAt(Instant.now())
                .lastFailedAt(Instant.now())
                .build();

        when(repository.findById(3L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.replay(3L))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Nenhum handler registrado");

        verify(strategy, never()).handle(any());
    }

    // -------------------------------------------------------------------------
    // replay() — invalid payload
    // -------------------------------------------------------------------------

    @Test
    void replay_invalidPayload_throwsRuleException() {
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(4L)
                .providerEventId("evt-004")
                .eventType(EVENT_TYPE)
                .payload("not-valid-json{{{{")
                .attempts(1)
                .firstFailedAt(Instant.now())
                .lastFailedAt(Instant.now())
                .build();

        when(repository.findById(4L)).thenReturn(Optional.of(entry));

        assertThatThrownBy(() -> service.replay(4L))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Falha ao deserializar");

        verify(strategy, never()).handle(any());
    }
}
