package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.PushNotificationProvider;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessPushNotificationService {

    private final PushNotificationProvider pushProvider;
    private final UserOrganizationRepository userOrganizationRepository;

    public void sendPush(NotificationEvent event) {
        log.info("[BusinessPush] Processando envio de PUSH para evento: {} - Org: {}", 
                event.getEventType(), event.getOrganizationCode());

        // 1. Resolver todos os usuários da organização que devem receber push
        List<User> recipients = userOrganizationRepository.findAllByOrganizationCode(event.getOrganizationCode())
                .stream()
                .map(UserOrganization::getUser)
                .toList();

        if (recipients.isEmpty()) {
            log.warn("[BusinessPush] Nenhum usuário encontrado para receber push na organização {}", 
                    event.getOrganizationCode());
            return;
        }

        // 2. Para cada usuário, montar payload e enviar via provider
        for (User user : recipients) {
            try {
                NotificationPayload payload = buildPayload(event, user);
                pushProvider.send(payload);
                log.debug("[BusinessPush] PUSH enviado para usuário {} (Evento: {})", 
                        user.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("[BusinessPush] Erro ao enviar PUSH para usuário {}: {}", 
                        user.getId(), e.getMessage());
            }
        }
    }

    private NotificationPayload buildPayload(NotificationEvent event, User user) {
        String subject = resolveSubject(event);
        String description = resolveDescription(event);

        return NotificationPayload.builder()
                .idUser(user.getId())
                .recipient(user.getEmail()) // Provider usará o idUser para buscar tokens, mas recipient pode ser útil para logs
                .recipientName(user.getName())
                .subject(subject)
                .content(description)
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

        return String.format("%s (ID: %s) %s.", ref, event.getReferenceId(), status);
    }

}
