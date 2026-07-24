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
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRecipient;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Canal WhatsApp (TASK-130 + TASK-131). Mesmo esqueleto de
 * {@link BusinessEmailNotificationService} (resolve destinatário → valida → monta payload →
 * envia → registra dispatch), mas com comportamentos que o e-mail não tem, de propósito:
 * - idempotência real (constraint única em {@link BusinessWhatsAppDispatch});
 * - fallback para e-mail quando o envio falha permanentemente;
 * - quota mensal por conta e rate limit diário por destinatário (TASK-131, custo por mensagem);
 * - envio adiado (não descartado) fora do horário comercial (TASK-131) — {@link #attemptSend}
 *   é chamado tanto no fluxo imediato quanto pelo {@code WhatsAppDeferredSendJob}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessWhatsAppNotificationService {

    private static final ZoneId BRASILIA_ZONE = ZoneId.of("America/Sao_Paulo");

    @Value("${notification.whatsapp.fallback-to-email:true}")
    private boolean fallbackToEmailEnabled;

    @Value("${notification.whatsapp.daily-limit-per-recipient:3}")
    private int dailyLimitPerRecipient;

    @Value("${notification.whatsapp.business-hours-start:8}")
    private int businessHoursStart;

    @Value("${notification.whatsapp.business-hours-end:20}")
    private int businessHoursEnd;

    private final WhatsAppNotificationProvider whatsAppProvider;
    private final BusinessWhatsAppDispatchRepository dispatchRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final BusinessEmailNotificationService emailNotificationService;
    private final BusinessWhatsAppQuotaService quotaService;
    private final Clock clock;

    @Transactional
    public void sendWhatsapp(NotificationEvent event, Set<NotificationChannel> resolvedChannels) {
        log.info("[BusinessWhatsApp] Recebido evento para processamento de WhatsApp: {} - Org: {}",
                event.getEventType(), event.getOrganizationCode());

        BusinessWhatsAppDispatch dispatch = findOrCreateDispatch(event, resolvedChannels.contains(NotificationChannel.EMAIL));

        if (dispatch.getStatus() == BusinessWhatsAppDispatchStatus.SENT) {
            log.info("[BusinessWhatsApp] Evento {} (org {}) já foi enviado com sucesso (wamid={}) — ignorando.",
                    event.getEventType(), event.getOrganizationCode(), dispatch.getWamid());
            return;
        }

        attemptSend(dispatch);
    }

    /**
     * Tenta enviar (ou reenviar) um dispatch. Chamado tanto pelo fluxo imediato
     * ({@link #sendWhatsapp}) quanto pelo {@code WhatsAppDeferredSendJob} para dispatches
     * represados em {@code PENDING_HOURS_WINDOW}. Revalida opt-in/quota/rate-limit sempre "a
     * quente" — opt-out precisa ser respeitado imediatamente, mesmo para mensagens já enfileiradas.
     */
    @Transactional
    public void attemptSend(BusinessWhatsAppDispatch dispatch) {
        Optional<UserOrganizationRecipient> recipientOpt = resolveRecipient(dispatch.getOrganizationCode());
        if (recipientOpt.isEmpty()) {
            log.warn("[BusinessWhatsApp] Destinatário não encontrado para organização {}", dispatch.getOrganizationCode());
            saveSkipped(dispatch, BusinessWhatsAppDispatchStatus.SKIPPED_INVALID_RECIPIENT);
            return;
        }
        User recipient = recipientOpt.get().user();
        String organizationName = recipientOpt.get().organizationName();

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

        if (!quotaService.canSend(dispatch.getOrganizationCode())) {
            log.warn("[BusinessWhatsApp] Cota mensal de WhatsApp atingida para organização {} — pulando.",
                    dispatch.getOrganizationCode());
            saveSkipped(dispatch, BusinessWhatsAppDispatchStatus.SKIPPED_QUOTA);
            return;
        }

        if (!withinDailyRateLimit(normalizedPhone.get())) {
            log.warn("[BusinessWhatsApp] Limite diário de mensagens atingido para o telefone {} — pulando.",
                    normalizedPhone.get());
            saveSkipped(dispatch, BusinessWhatsAppDispatchStatus.SKIPPED_RATE_LIMIT);
            return;
        }

        if (!isWithinBusinessHours()) {
            log.info("[BusinessWhatsApp] Fora do horário comercial ({}h-{}h Brasília) — adiando envio para o evento {}.",
                    businessHoursStart, businessHoursEnd, dispatch.getEventType());
            dispatch.setStatus(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW);
            dispatchRepository.save(dispatch);
            return;
        }

        send(dispatch, recipient, organizationName, normalizedPhone.get());
    }

    private void send(BusinessWhatsAppDispatch dispatch, User recipient, String organizationName, String normalizedPhone) {
        try {
            NotificationPayload payload = buildPayload(dispatch, recipient, organizationName, normalizedPhone);
            WhatsAppSendResult result = whatsAppProvider.sendTemplateMessage(payload);

            dispatch.setStatus(BusinessWhatsAppDispatchStatus.SENT);
            dispatch.setWamid(result.wamid());
            dispatch.setSentAt(Instant.now());
            dispatch.setErrorMessage(null);
            dispatchRepository.save(dispatch);

            log.info("[BusinessWhatsApp] Mensagem enviada com sucesso: wamid={} recipient={} (Evento: {})",
                    result.wamid(), normalizedPhone, dispatch.getEventType());

        } catch (WhatsAppPermanentException | WhatsAppTransientException e) {
            // Retry de falha transitória já foi tentado e esgotado dentro do WhatsAppClient
            // (TASK-129, Resilience4j) — qualquer exceção que chegue até aqui é uma falha final.
            log.error("[BusinessWhatsApp] Falha ao enviar WhatsApp para evento {}: {}",
                    dispatch.getEventType(), e.getMessage());
            dispatch.setStatus(BusinessWhatsAppDispatchStatus.FAILED);
            dispatch.setErrorMessage(e.getMessage());
            dispatchRepository.save(dispatch);

            fallbackToEmailIfNeeded(dispatch);
        }
    }

    private void fallbackToEmailIfNeeded(BusinessWhatsAppDispatch dispatch) {
        if (!fallbackToEmailEnabled) {
            return;
        }
        if (dispatch.isEmailAlreadyCovered()) {
            log.info("[BusinessWhatsApp] Fallback para e-mail não necessário — evento {} já inclui EMAIL.",
                    dispatch.getEventType());
            return;
        }

        log.info("[BusinessWhatsApp] Acionando fallback para e-mail no evento {} (org {})",
                dispatch.getEventType(), dispatch.getOrganizationCode());
        try {
            emailNotificationService.sendEmail(toNotificationEvent(dispatch));
        } catch (Exception e) {
            log.error("[BusinessWhatsApp] Falha ao acionar fallback de e-mail para evento {}: {}",
                    dispatch.getEventType(), e.getMessage());
        }
    }

    private NotificationEvent toNotificationEvent(BusinessWhatsAppDispatch dispatch) {
        return NotificationEvent.builder()
                .organizationCode(dispatch.getOrganizationCode())
                .eventType(dispatch.getEventType())
                .referenceType(dispatch.getReferenceType())
                .referenceId(dispatch.getReferenceId())
                .referenceLabel(dispatch.getReferenceLabel())
                .dueDate(dispatch.getDueDate())
                .daysOffset(dispatch.getDaysOffset())
                .build();
    }

    // TASK-131: cap simples de mensagens por destinatário/dia — não é agregação em "resumo do
    // dia" (exigiria um novo template HSM aprovado na Meta especificamente para múltiplos itens,
    // fora do escopo desta task; decisão registrada no card). Eventos genuinamente distintos
    // (itens diferentes) ainda geram mensagens separadas até esse limite.
    private boolean withinDailyRateLimit(String phone) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(BRASILIA_ZONE);
        Instant startOfDay = now.toLocalDate().atStartOfDay(BRASILIA_ZONE).toInstant();
        long sentToday = dispatchRepository.countSentToPhoneSince(phone, startOfDay);
        return sentToday < dailyLimitPerRecipient;
    }

    // TASK-131: horário comercial em America/Sao_Paulo (Brasília) — não configurável por
    // timezone, produto é Brasil-only. Fora da janela, o dispatch fica PENDING_HOURS_WINDOW até o
    // WhatsAppDeferredSendJob processá-lo. Usa o Clock injetado (não Clock.systemDefaultZone()
    // direto) para permitir controlar o "agora" em testes.
    public boolean isWithinBusinessHours() {
        int hour = ZonedDateTime.now(clock).withZoneSameInstant(BRASILIA_ZONE).getHour();
        return hour >= businessHoursStart && hour < businessHoursEnd;
    }

    private BusinessWhatsAppDispatch findOrCreateDispatch(NotificationEvent event, boolean emailAlreadyCovered) {
        return dispatchRepository
                .findByOrganizationCodeAndEventTypeAndReferenceTypeAndReferenceIdAndDueDateAndDaysOffset(
                        event.getOrganizationCode(), event.getEventType(), event.getReferenceType(),
                        event.getReferenceId(), event.getDueDate(), event.getDaysOffset())
                .orElseGet(() -> BusinessWhatsAppDispatch.builder()
                        .organizationCode(event.getOrganizationCode())
                        .eventType(event.getEventType())
                        .referenceType(event.getReferenceType())
                        .referenceId(event.getReferenceId())
                        .referenceLabel(event.getReferenceLabel())
                        .dueDate(event.getDueDate())
                        .daysOffset(event.getDaysOffset())
                        .emailAlreadyCovered(emailAlreadyCovered)
                        .status(BusinessWhatsAppDispatchStatus.PENDING)
                        .build());
    }

    private void saveSkipped(BusinessWhatsAppDispatch dispatch, BusinessWhatsAppDispatchStatus status) {
        dispatch.setStatus(status);
        dispatchRepository.save(dispatch);
    }

    private Optional<UserOrganizationRecipient> resolveRecipient(String organizationCode) {
        return userOrganizationRepository.findRecipientsWithOrganizationName(organizationCode)
                .stream()
                .findFirst();
    }

    private NotificationPayload buildPayload(BusinessWhatsAppDispatch dispatch, User user, String organizationName,
                                              String normalizedPhone) {
        String itemName = dispatch.getReferenceLabel();
        String dueDate = dispatch.getDueDate() != null ? dispatch.getDueDate().toString() : "";

        return NotificationPayload.builder()
                .recipient(normalizedPhone)
                .recipientName(user.getName())
                .templateData(Map.of(
                        "recipientName", user.getName(),
                        "itemName", itemName != null ? itemName : "",
                        "companyName", organizationName != null ? organizationName : "",
                        "dueDate", dueDate,
                        "itemId", String.valueOf(dispatch.getReferenceId())))
                .build();
    }
}
