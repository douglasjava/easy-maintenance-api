package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessEmailDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessEmailDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.EmailNotificationProvider;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessEmailDispatchRepository;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailRetryJob {

    static final int MAX_RETRIES = 2;
    static final long RETRY_WINDOW_HOURS = 24;
    static final long MIN_INTERVAL_MINUTES = 15;

    private final BusinessEmailDispatchRepository dispatchRepository;
    private final EmailNotificationProvider emailProvider;
    private final UserOrganizationRepository userOrganizationRepository;

    @Scheduled(cron = "${email.retry.cron:0 0/30 * * * *}")
    @SchedulerLock(name = "EmailRetryJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT5M")
    @Transactional
    public void run() {
        log.info("[EmailRetryJob] Iniciando job de reenvio de e-mails com falha.");
        TenantContext.setSystemContext();
        try {
            Instant cutoff = Instant.now().minus(RETRY_WINDOW_HOURS, ChronoUnit.HOURS);
            Instant lastRetryBefore = Instant.now().minus(MIN_INTERVAL_MINUTES, ChronoUnit.MINUTES);

            List<BusinessEmailDispatch> eligible = dispatchRepository.findEligibleForRetry(
                    BusinessEmailDispatchStatus.FAILED, MAX_RETRIES, cutoff, lastRetryBefore);

            if (eligible.isEmpty()) {
                log.info("[EmailRetryJob] Nenhum e-mail elegível para reenvio.");
                return;
            }

            log.info("[EmailRetryJob] {} e-mail(s) elegível(is) para reenvio.", eligible.size());
            for (BusinessEmailDispatch dispatch : eligible) {
                retryDispatch(dispatch);
            }
            log.info("[EmailRetryJob] Job de reenvio finalizado.");

        } catch (Exception e) {
            log.error("[EmailRetryJob] Erro inesperado no job de reenvio: {}", e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private void retryDispatch(BusinessEmailDispatch dispatch) {
        try {
            String recipientName = resolveRecipientName(dispatch);
            String subject;
            String htmlContent;

            if (dispatch.getHtmlContent() != null) {
                // Critical email: reuse stored pre-rendered content
                subject = dispatch.getSubject();
                htmlContent = dispatch.getHtmlContent();
            } else {
                // Operational email: reconstruct dynamically from event type
                subject = resolveSubject(dispatch);
                String description = resolveDescription(dispatch);
                htmlContent = EmailTemplateHelper.generateNotificationEventHtml(recipientName, subject, description);
            }

            NotificationPayload payload = NotificationPayload.builder()
                    .recipient(dispatch.getRecipientEmail())
                    .recipientName(recipientName)
                    .subject(subject)
                    .content(subject)
                    .htmlContent(htmlContent)
                    .build();

            emailProvider.send(payload);

            int nextCount = dispatch.getRetryCount() + 1;
            dispatch.setRetryCount(nextCount);
            dispatch.setLastRetryAt(Instant.now());
            dispatch.setStatus(BusinessEmailDispatchStatus.SENT);
            dispatch.setSentAt(Instant.now());
            dispatchRepository.save(dispatch);

            log.info("[EmailRetryJob] Reenvio bem-sucedido: dispatch={}, recipient={}, tentativa={}",
                    dispatch.getId(), dispatch.getRecipientEmail(), nextCount);

        } catch (Exception e) {
            int nextCount = dispatch.getRetryCount() + 1;
            dispatch.setRetryCount(nextCount);
            dispatch.setLastRetryAt(Instant.now());
            dispatch.setErrorMessage(e.getMessage());
            dispatchRepository.save(dispatch);

            if (nextCount >= MAX_RETRIES) {
                log.error("[EmailRetryJob] Falha definitiva após {} tentativas: dispatch={}, recipient={}, event={}, error={}",
                        nextCount, dispatch.getId(), dispatch.getRecipientEmail(), dispatch.getEventType(), e.getMessage());
            } else {
                log.warn("[EmailRetryJob] Reenvio falhou (tentativa {}): dispatch={}, error={}",
                        nextCount, dispatch.getId(), e.getMessage());
            }
        }
    }

    private String resolveRecipientName(BusinessEmailDispatch dispatch) {
        if (dispatch.getOrganizationCode() == null) {
            return dispatch.getRecipientEmail();
        }
        return userOrganizationRepository.findAllByOrganizationCode(dispatch.getOrganizationCode())
                .stream()
                .map(uo -> uo.getUser().getName())
                .findFirst()
                .orElse(dispatch.getRecipientEmail());
    }

    private String resolveSubject(BusinessEmailDispatch dispatch) {
        return switch (dispatch.getEventType()) {
            case ITEM_NEAR_DUE -> "Item Próximo do Vencimento";
            case ITEM_OVERDUE -> "Item Vencido";
            case MAINTENANCE_NEAR_DUE -> "Manutenção Próxima do Vencimento";
            case MAINTENANCE_OVERDUE -> "Manutenção Vencida";
            default -> dispatch.getEventType().name();
        };
    }

    private String resolveDescription(BusinessEmailDispatch dispatch) {
        if (dispatch.getReferenceType() == null || dispatch.getReferenceId() == null) {
            return "Verifique o sistema para mais detalhes.";
        }
        String ref = dispatch.getReferenceType() == NotificationReferenceType.ITEM ? "O item" : "A manutenção";
        return String.format("%s (ID: %s) requer sua atenção. Por favor, verifique no sistema.",
                ref, dispatch.getReferenceId());
    }
}
