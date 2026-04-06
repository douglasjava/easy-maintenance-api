package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessEmailDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessEmailDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.EmailNotificationProvider;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessEmailDispatchRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessEmailNotificationService {

    private final BusinessEmailQuotaService quotaService;
    private final EmailNotificationProvider emailProvider;
    private final BusinessEmailDispatchRepository dispatchRepository;
    private final UserOrganizationRepository userOrganizationRepository;

    @Transactional
    public void sendEmail(NotificationEvent event) {
        log.info("[BusinessEmail] Recebido evento para processamento de e-mail: {} - Org: {}", 
                event.getEventType(), event.getOrganizationCode());

        BusinessEmailDispatch dispatch = BusinessEmailDispatch.builder()
                .organizationCode(event.getOrganizationCode())
                .eventType(event.getEventType())
                .referenceType(event.getReferenceType())
                .referenceId(event.getReferenceId())
                .status(BusinessEmailDispatchStatus.PENDING)
                .build();

        try {
            // 1. Validar se o tipo de evento suporta e-mail (nesta etapa, todos os eventos de negócio do orquestrador suportam)
            if (!isEventEligible(event)) {
                log.warn("[BusinessEmail] Evento {} não é elegível para envio de e-mail.", event.getEventType());
                dispatch.setStatus(BusinessEmailDispatchStatus.SKIPPED_UNSUPPORTED_EVENT);
                dispatchRepository.save(dispatch);
                return;
            }

            // 2. Resolver destinatário
            Optional<User> recipientOpt = resolveRecipient(event);
            if (recipientOpt.isEmpty()) {
                log.warn("[BusinessEmail] Destinatário não encontrado para organização {}", event.getOrganizationCode());
                dispatch.setStatus(BusinessEmailDispatchStatus.SKIPPED_INVALID_RECIPIENT);
                dispatchRepository.save(dispatch);
                return;
            }
            User recipient = recipientOpt.get();
            dispatch.setRecipientEmail(recipient.getEmail());

            if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                log.warn("[BusinessEmail] E-mail do destinatário está vazio para usuário {}", recipient.getId());
                dispatch.setStatus(BusinessEmailDispatchStatus.SKIPPED_INVALID_RECIPIENT);
                dispatchRepository.save(dispatch);
                return;
            }

            // 3. Validar quota
            if (!quotaService.canSend(event.getOrganizationCode())) {
                log.warn("[BusinessEmail] Limite mensal de e-mails atingido para organização {}", event.getOrganizationCode());
                dispatch.setStatus(BusinessEmailDispatchStatus.SKIPPED_LIMIT);
                dispatchRepository.save(dispatch);
                return;
            }

            // 4. Montar Payload
            NotificationPayload payload = buildPayload(event, recipient);

            // 5. Enviar via Provider
            emailProvider.send(payload);

            // 6. Registrar sucesso
            dispatch.setStatus(BusinessEmailDispatchStatus.SENT);
            dispatch.setSentAt(Instant.now());
            dispatchRepository.save(dispatch);
            
            log.info("[BusinessEmail] E-mail enviado com sucesso para {} (Evento: {})", 
                    recipient.getEmail(), event.getEventType());

        } catch (Exception e) {
            log.error("[BusinessEmail] Erro ao processar envio de e-mail para evento {}: {}", 
                    event.getEventType(), e.getMessage());
            dispatch.setStatus(BusinessEmailDispatchStatus.FAILED);
            dispatch.setErrorMessage(e.getMessage());
            dispatchRepository.save(dispatch);
        }
    }

    private boolean isEventEligible(NotificationEvent event) {
        // Por enquanto, todos os eventos mapeados para e-mail no resolver são elegíveis
        return true;
    }

    private Optional<User> resolveRecipient(NotificationEvent event) {
        // Regra simples: Pega o primeiro usuário vinculado à organização (geralmente o dono/admin)
        // No futuro, isso pode ser mais complexo (preferências de notificação)
        return userOrganizationRepository.findAllByOrganizationCode(event.getOrganizationCode())
                .stream()
                .map(UserOrganization::getUser)
                .findFirst();
    }

    private NotificationPayload buildPayload(NotificationEvent event, User user) {
        String subject = resolveSubject(event);
        String description = resolveDescription(event);
        
        String htmlContent = EmailTemplateHelper.generateNotificationEventHtml(
                user.getName(), 
                subject, 
                description
        );

        return NotificationPayload.builder()
                .recipient(user.getEmail())
                .recipientName(user.getName())
                .subject(subject)
                .content(description)
                .htmlContent(htmlContent)
                .build();
    }

    private String resolveSubject(NotificationEvent event) {
        return switch (event.getEventType()) {
            case ITEM_NEAR_DUE -> "Item Próximo do Vencimento";
            case ITEM_OVERDUE -> "Item Vencido";
            case MAINTENANCE_NEAR_DUE -> "Manutenção Próxima do Vencimento";
            case MAINTENANCE_OVERDUE -> "Manutenção Vencida";
        };
    }

    private String resolveDescription(NotificationEvent event) {
        String ref = event.getReferenceType() == NotificationReferenceType.ITEM ? "O item" : "A manutenção";
        String status = event.getDaysOffset() >= 0 ? "vence em " + event.getDaysOffset() + " dias" : "está vencido(a)";
        
        if (event.getDaysOffset() == 0) {
            status = "vence hoje";
        } else if (event.getDaysOffset() < 0) {
            status = "está em atraso há " + Math.abs(event.getDaysOffset()) + " dias";
        }

        return String.format("%s (ID: %s) %s. Data de vencimento: %s.", 
                ref, event.getReferenceId(), status, event.getDueDate());
    }

}
