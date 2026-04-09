package com.brainbyte.easy_maintenance.webhooks.asaas.service;

import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.AsaasWebhookStrategy;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookEvent;
import com.brainbyte.easy_maintenance.webhooks.commons.domain.enums.WebhookEventStatus;
import com.brainbyte.easy_maintenance.webhooks.commons.service.WebhookEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsaasWebhookServiceTest {

    @Mock
    private WebhookEventService webhookEventService;

    @Mock
    private AsaasWebhookStrategy strategy;

    private AsaasWebhookService service;

    private AsaasDTO.WebhookCheckoutEvent event;

    @BeforeEach
    void setUp() {
        when(strategy.getEventType()).thenReturn("CHECKOUT_COMPLETED");
        service = new AsaasWebhookService(webhookEventService, new ObjectMapper(), List.of(strategy));

        event = new AsaasDTO.WebhookCheckoutEvent(
                "evt-001", "CHECKOUT_COMPLETED", "2026-04-07", null, null, null, null
        );
    }

    @Test
    void shouldIgnoreNullEvent() {
        service.processEvent(null);
        verifyNoInteractions(webhookEventService);
    }

    @Test
    void shouldIgnoreAlreadyProcessedEvent() {
        when(webhookEventService.findByProviderEventId("evt-001"))
                .thenReturn(Optional.of(new WebhookEvent()));

        service.processEvent(event);

        verify(webhookEventService, never()).save(any());
        verify(strategy, never()).handle(any());
    }

    @Test
    void shouldSaveAndProcessNewEvent() {
        WebhookEvent saved = WebhookEvent.builder()
                .providerEventId("evt-001")
                .status(WebhookEventStatus.RECEIVED)
                .build();

        when(webhookEventService.findByProviderEventId("evt-001")).thenReturn(Optional.empty());
        when(webhookEventService.save(any())).thenReturn(saved);

        service.processEvent(event);

        verify(strategy).handle(event);

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventService, atLeast(2)).save(captor.capture());

        List<WebhookEvent> saves = captor.getAllValues();
        WebhookEvent finalSave = saves.get(saves.size() - 1);
        assertEquals(WebhookEventStatus.PROCESSED, finalSave.getStatus());
        assertNotNull(finalSave.getProcessedAt());
    }

    @Test
    void shouldHandleRaceConditionOnDuplicateInsert() {
        when(webhookEventService.findByProviderEventId("evt-001")).thenReturn(Optional.empty());
        when(webhookEventService.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertDoesNotThrow(() -> service.processEvent(event));
        verify(strategy, never()).handle(any());
    }

    @Test
    void shouldMarkEventAsErrorWhenStrategyFails() {
        WebhookEvent saved = WebhookEvent.builder()
                .providerEventId("evt-001")
                .status(WebhookEventStatus.RECEIVED)
                .build();

        when(webhookEventService.findByProviderEventId("evt-001")).thenReturn(Optional.empty());
        when(webhookEventService.save(any())).thenReturn(saved);
        doThrow(new RuntimeException("strategy error")).when(strategy).handle(any());

        assertDoesNotThrow(() -> service.processEvent(event));

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventService, atLeast(2)).save(captor.capture());

        List<WebhookEvent> saves = captor.getAllValues();
        WebhookEvent errorSave = saves.get(saves.size() - 1);
        assertEquals(WebhookEventStatus.ERROR, errorSave.getStatus());
        assertEquals("strategy error", errorSave.getErrorMessage());
    }

    @Test
    void shouldHandleEventWithNoRegisteredStrategy() {
        AsaasDTO.WebhookCheckoutEvent unknownEvent = new AsaasDTO.WebhookCheckoutEvent(
                "evt-002", "UNKNOWN_EVENT", "2026-04-07", null, null, null, null
        );

        WebhookEvent saved = WebhookEvent.builder()
                .providerEventId("evt-002")
                .status(WebhookEventStatus.RECEIVED)
                .build();

        when(webhookEventService.findByProviderEventId("evt-002")).thenReturn(Optional.empty());
        when(webhookEventService.save(any())).thenReturn(saved);

        assertDoesNotThrow(() -> service.processEvent(unknownEvent));
        verify(strategy, never()).handle(any());

        ArgumentCaptor<WebhookEvent> captor = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventService, atLeast(2)).save(captor.capture());
        List<WebhookEvent> saves = captor.getAllValues();
        assertEquals(WebhookEventStatus.PROCESSED, saves.get(saves.size() - 1).getStatus());
    }
}
