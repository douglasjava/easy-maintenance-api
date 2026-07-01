package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.InAppNotification;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.InAppNotificationResponse;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.InAppNotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.InAppNotificationRepository;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InAppNotificationService {

    private final InAppNotificationRepository repository;
    private final UserOrganizationRepository userOrganizationRepository;

    public List<InAppNotificationResponse> listForUser(Long userId) {
        return TenantContext.get()
                .map(orgCode -> repository.findTop20ForUserAndOrg(userId, orgCode, PageRequest.of(0, 20))
                        .stream().map(this::toResponse).toList())
                .orElseGet(() -> repository.findTop20ByUserIdOrderByCreatedAtDesc(userId)
                        .stream().map(this::toResponse).toList());
    }

    public long countUnread(Long userId) {
        return TenantContext.get()
                .map(orgCode -> repository.countUnreadForUserAndOrg(userId, orgCode))
                .orElseGet(() -> repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        InAppNotification notification = repository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Notificação não encontrada"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            repository.save(notification);
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        TenantContext.get().ifPresentOrElse(
                orgCode -> repository.markAllReadForUserAndOrg(userId, orgCode),
                () -> repository.markAllReadByUserId(userId)
        );
    }

    /**
     * Saves an in-app notification for every user belonging to the given organization.
     */
    public void saveForOrg(String orgCode, String title, String body,
                           InAppNotificationType type, Long referenceId, String referenceLabel) {
        List<Long> userIds = userOrganizationRepository.findAllByOrganizationCode(orgCode)
                .stream()
                .map(UserOrganization::getUser)
                .map(u -> u.getId())
                .toList();

        if (userIds.isEmpty()) {
            log.debug("[InAppNotification] Nenhum usuário para org={}", orgCode);
            return;
        }

        List<InAppNotification> notifications = userIds.stream()
                .map(userId -> InAppNotification.builder()
                        .userId(userId)
                        .orgCode(orgCode)
                        .title(title)
                        .body(body)
                        .type(type)
                        .referenceId(referenceId)
                        .referenceLabel(referenceLabel)
                        .build())
                .toList();

        repository.saveAll(notifications);
        log.debug("[InAppNotification] {} notificações salvas para org={}", notifications.size(), orgCode);
    }

    /**
     * Saves an in-app notification for a single user.
     */
    public void saveForUser(Long userId, String title, String body,
                            InAppNotificationType type, Long referenceId, String referenceLabel) {
        repository.save(InAppNotification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .referenceId(referenceId)
                .referenceLabel(referenceLabel)
                .build());
    }

    private InAppNotificationResponse toResponse(InAppNotification n) {
        return new InAppNotificationResponse(
                n.getId(),
                n.getTitle(),
                n.getBody(),
                n.getType(),
                n.getReferenceId(),
                n.getReferenceLabel(),
                n.getReadAt() != null,
                n.getCreatedAt(),
                n.getOrgCode()
        );
    }
}
