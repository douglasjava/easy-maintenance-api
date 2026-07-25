package com.brainbyte.easy_maintenance.webhooks.whatsapp.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.WhatsAppDeliveryStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.webhooks.whatsapp.dto.WhatsAppWebhookDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    // Ranking de avanço monotônico: um evento só é aplicado se representar progresso em relação
    // ao delivery_status já persistido — evita que um evento atrasado/fora de ordem (ex.: DELIVERED
    // chegando depois de READ já ter sido processado) regrida o status. SENT e FAILED compartilham
    // rank porque ambos normalmente sucedem SENT diretamente (branches mutuamente exclusivos).
    private static final Map<WhatsAppDeliveryStatus, Integer> STATUS_RANK = Map.of(
            WhatsAppDeliveryStatus.SENT, 1,
            WhatsAppDeliveryStatus.FAILED, 2,
            WhatsAppDeliveryStatus.DELIVERED, 2,
            WhatsAppDeliveryStatus.READ, 3
    );

    private final BusinessWhatsAppDispatchRepository dispatchRepository;
    private final ObjectMapper objectMapper;

    @Async
    @Transactional
    public void processEvent(String rawBody) {
        WhatsAppWebhookDTO.WebhookEventPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WhatsAppWebhookDTO.WebhookEventPayload.class);
        } catch (Exception e) {
            log.error("[WhatsAppWebhook] Falha ao parsear payload recebido: {}", e.getMessage());
            return;
        }

        if (payload.entry() == null) {
            return;
        }

        for (WhatsAppWebhookDTO.Entry entry : payload.entry()) {
            if (entry.changes() == null) {
                continue;
            }
            for (WhatsAppWebhookDTO.Change change : entry.changes()) {
                processChange(change.value());
            }
        }
    }

    private void processChange(WhatsAppWebhookDTO.Value value) {
        if (value == null) {
            return;
        }

        if (value.statuses() != null) {
            for (WhatsAppWebhookDTO.StatusEntry status : value.statuses()) {
                applyStatus(status);
            }
        }

        if (value.messages() != null) {
            for (WhatsAppWebhookDTO.InboundMessage message : value.messages()) {
                log.info("[WhatsAppWebhook] Mensagem inbound recebida — wamid={}, de={}, tipo={}",
                        message.id(), maskPhone(message.from()), message.type());
            }
        }
    }

    private void applyStatus(WhatsAppWebhookDTO.StatusEntry statusEntry) {
        if (statusEntry == null || statusEntry.id() == null || statusEntry.status() == null) {
            return;
        }

        WhatsAppDeliveryStatus incoming = mapStatus(statusEntry.status());
        if (incoming == null) {
            log.info("[WhatsAppWebhook] Status '{}' não reconhecido para wamid {} — ignorado.",
                    statusEntry.status(), statusEntry.id());
            return;
        }

        Optional<BusinessWhatsAppDispatch> dispatchOpt = dispatchRepository.findByWamid(statusEntry.id());
        if (dispatchOpt.isEmpty()) {
            log.warn("[WhatsAppWebhook] wamid {} não corresponde a nenhum dispatch conhecido — evento ignorado.",
                    statusEntry.id());
            return;
        }

        BusinessWhatsAppDispatch dispatch = dispatchOpt.get();
        if (!isAdvance(dispatch.getDeliveryStatus(), incoming)) {
            log.info("[WhatsAppWebhook] Evento '{}' para wamid {} ignorado — delivery_status atual '{}' já é igual ou mais avançado.",
                    incoming, statusEntry.id(), dispatch.getDeliveryStatus());
            return;
        }

        dispatch.setDeliveryStatus(incoming);
        Instant eventTimestamp = parseTimestamp(statusEntry.timestamp());

        switch (incoming) {
            case DELIVERED -> dispatch.setDeliveredAt(eventTimestamp);
            case READ -> dispatch.setReadAt(eventTimestamp);
            case FAILED -> applyFailure(dispatch, statusEntry);
            default -> {
                // SENT não tem campo de timestamp dedicado na entidade (sentAt já é setado no envio).
            }
        }

        dispatchRepository.save(dispatch);
        log.info("[WhatsAppWebhook] Dispatch atualizado — wamid={}, deliveryStatus={}", statusEntry.id(), incoming);
    }

    private void applyFailure(BusinessWhatsAppDispatch dispatch, WhatsAppWebhookDTO.StatusEntry statusEntry) {
        if (statusEntry.errors() == null || statusEntry.errors().isEmpty()) {
            return;
        }
        WhatsAppWebhookDTO.StatusError error = statusEntry.errors().get(0);
        dispatch.setFailedErrorCode(error.code() != null ? String.valueOf(error.code()) : null);
        dispatch.setFailedErrorMessage(error.message() != null ? error.message() : error.title());
    }

    private boolean isAdvance(WhatsAppDeliveryStatus current, WhatsAppDeliveryStatus incoming) {
        if (current == null) {
            return true;
        }
        if (current == incoming) {
            return false;
        }
        return STATUS_RANK.get(incoming) > STATUS_RANK.get(current);
    }

    private WhatsAppDeliveryStatus mapStatus(String status) {
        return switch (status.toLowerCase()) {
            case "sent" -> WhatsAppDeliveryStatus.SENT;
            case "delivered" -> WhatsAppDeliveryStatus.DELIVERED;
            case "read" -> WhatsAppDeliveryStatus.READ;
            case "failed" -> WhatsAppDeliveryStatus.FAILED;
            default -> null;
        };
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(timestamp));
        } catch (NumberFormatException e) {
            return Instant.now();
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}
