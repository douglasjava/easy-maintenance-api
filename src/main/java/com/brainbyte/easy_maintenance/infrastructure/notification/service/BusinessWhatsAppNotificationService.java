package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppPermanentException;
import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppTransientException;
import com.brainbyte.easy_maintenance.commons.utils.PhoneNumberNormalizer;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppSendResult;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.WhatsAppNotificationProvider;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Canal WhatsApp (TASK-130). Mesmo esqueleto de {@link BusinessEmailNotificationService}
 * (resolve destinatário → valida → monta payload → envia → registra dispatch), mas com dois
 * comportamentos que o e-mail não tem, de propósito (ver contexto no card):
 * - idempotência real (constraint única em {@link BusinessWhatsAppDispatch}, não implícita);
 * - fallback para e-mail quando o envio falha permanentemente.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessWhatsAppNotificationService {

    @Value("${notification.whatsapp.fallback-to-email:true}")
    private boolean fallbackToEmailEnabled;

    private final WhatsAppNotificationProvider whatsAppProvider;
    private final BusinessWhatsAppDispatchRepository dispatchRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final BusinessEmailNotificationService emailNotificationService;

    @Transactional
    public void sendWhatsapp(NotificationEvent event, Set<NotificationChannel> resolvedChannels) {
        log.info("[BusinessWhatsApp] Recebido evento para processamento de WhatsApp: {} - Org: {}",
                event.getEventType(), event.getOrganizationCode());

        BusinessWhatsAppDispatch dispatch = findOrCreateDispatch(event);

        if (dispatch.getStatus() == BusinessWhatsAppDispatchStatus.SENT) {
            log.info("[BusinessWhatsApp] Evento {} (org {}) já foi enviado com sucesso (wamid={}) — ignorando.",
                    event.getEventType(), event.getOrganizationCode(), dispatch.getWamid());
            return;
        }

        Optional<User> recipientOpt = resolveRecipient(event);
        if (recipientOpt.isEmpty()) {
            log.warn("[BusinessWhatsApp] Destinatário não encontrado para organização {}", event.getOrganizationCode());
            saveSkipped(dispatch, BusinessWhatsAppDispatchStatus.SKIPPED_INVALID_RECIPIENT);
            return;
        }
        User recipient = recipientOpt.get();

        if (!recipient.isWhatsappOptIn()) {
            log.info("[BusinessWhatsApp] Usuário {} não tem opt-in de WhatsApp — pulando.", recipient.getId());
            saveSkipped(dispatch, BusinessWhatsAppDispatchStatus.SKIPPED_OPT_OUT);
            return;
        }

        // Telefone já é salvo normalizado em E.164 pela UsersService (TASK-122); revalidar aqui é
        // defensivo (ex.: dado gravado antes dessa regra existir) — não deveria falhar em uso normal.
        Optional<String> normalizedPhone = PhoneNumberNormalizer.toE164BR(recipient.getPhoneNumber());
        if (normalizedPhone.isEmpty()) {
            log.warn("[BusinessWhatsApp] Telefone ausente ou inválido para usuário {} — pulando.", recipient.getId());
            saveSkipped(dispatch, BusinessWhatsAppDispatchStatus.SKIPPED_INVALID_RECIPIENT);
            return;
        }

        dispatch.setRecipientPhone(normalizedPhone.get());

        try {
            NotificationPayload payload = buildPayload(event, recipient, normalizedPhone.get());
            WhatsAppSendResult result = whatsAppProvider.sendTemplateMessage(payload);

            dispatch.setStatus(BusinessWhatsAppDispatchStatus.SENT);
            dispatch.setWamid(result.wamid());
            dispatch.setSentAt(Instant.now());
            dispatch.setErrorMessage(null);
            dispatchRepository.save(dispatch);

            log.info("[BusinessWhatsApp] Mensagem enviada com sucesso: wamid={} recipient={} (Evento: {})",
                    result.wamid(), normalizedPhone.get(), event.getEventType());

        } catch (WhatsAppPermanentException | WhatsAppTransientException e) {
            // Retry de falha transitória já foi tentado e esgotado dentro do WhatsAppClient
            // (TASK-129, Resilience4j) — qualquer exceção que chegue até aqui é uma falha final.
            log.error("[BusinessWhatsApp] Falha ao enviar WhatsApp para evento {}: {}",
                    event.getEventType(), e.getMessage());
            dispatch.setStatus(BusinessWhatsAppDispatchStatus.FAILED);
            dispatch.setErrorMessage(e.getMessage());
            dispatchRepository.save(dispatch);

            fallbackToEmailIfNeeded(event, resolvedChannels);
        }
    }

    private void fallbackToEmailIfNeeded(NotificationEvent event, Set<NotificationChannel> resolvedChannels) {
        if (!fallbackToEmailEnabled) {
            return;
        }
        if (resolvedChannels.contains(NotificationChannel.EMAIL)) {
            // E-mail já vai ser (ou já foi) enviado pelo orchestrator para este mesmo evento —
            // disparar de novo aqui duplicaria o aviso.
            log.info("[BusinessWhatsApp] Fallback para e-mail não necessário — evento {} já inclui EMAIL.",
                    event.getEventType());
            return;
        }

        log.info("[BusinessWhatsApp] Acionando fallback para e-mail no evento {} (org {})",
                event.getEventType(), event.getOrganizationCode());
        try {
            emailNotificationService.sendEmail(event);
        } catch (Exception e) {
            log.error("[BusinessWhatsApp] Falha ao acionar fallback de e-mail para evento {}: {}",
                    event.getEventType(), e.getMessage());
        }
    }

    private BusinessWhatsAppDispatch findOrCreateDispatch(NotificationEvent event) {
        return dispatchRepository
                .findByOrganizationCodeAndEventTypeAndReferenceTypeAndReferenceIdAndDueDateAndDaysOffset(
                        event.getOrganizationCode(), event.getEventType(), event.getReferenceType(),
                        event.getReferenceId(), event.getDueDate(), event.getDaysOffset())
                .orElseGet(() -> BusinessWhatsAppDispatch.builder()
                        .organizationCode(event.getOrganizationCode())
                        .eventType(event.getEventType())
                        .referenceType(event.getReferenceType())
                        .referenceId(event.getReferenceId())
                        .dueDate(event.getDueDate())
                        .daysOffset(event.getDaysOffset())
                        .status(BusinessWhatsAppDispatchStatus.PENDING)
                        .build());
    }

    private void saveSkipped(BusinessWhatsAppDispatch dispatch, BusinessWhatsAppDispatchStatus status) {
        dispatch.setStatus(status);
        dispatchRepository.save(dispatch);
    }

    private Optional<User> resolveRecipient(NotificationEvent event) {
        return userOrganizationRepository.findAllByOrganizationCodeWithUser(event.getOrganizationCode())
                .stream()
                .map(UserOrganization::getUser)
                .findFirst();
    }

    private NotificationPayload buildPayload(NotificationEvent event, User user, String normalizedPhone) {
        String itemName = event.getReferenceLabel();
        String dueDate = event.getDueDate() != null ? event.getDueDate().toString() : "";

        return NotificationPayload.builder()
                .recipient(normalizedPhone)
                .recipientName(user.getName())
                .templateData(Map.of(
                        "recipientName", user.getName(),
                        "itemName", itemName != null ? itemName : "",
                        "dueDate", dueDate))
                .build();
    }
}
