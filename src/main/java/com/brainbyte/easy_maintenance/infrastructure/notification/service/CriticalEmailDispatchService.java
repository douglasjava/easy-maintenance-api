package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.infrastructure.mail.MailService;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessEmailDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessEmailDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessEmailDispatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Sends critical transactional emails (trial expiring, payment, password reset, etc.)
 * and persists a BusinessEmailDispatch record so the EmailRetryJob can retry on failure.
 *
 * Retryable emails store subject + html_content so the retry job can resend the exact
 * same content without reconstructing complex templates.
 * Non-retryable emails (e.g. password reset with 30-min token) are tracked for
 * observability but excluded from the job-based retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CriticalEmailDispatchService {

    private final BusinessEmailDispatchRepository dispatchRepository;
    private final MailService mailService;

    @Transactional
    public void send(String recipientEmail,
                     String recipientName,
                     String organizationCode,
                     NotificationEventType eventType,
                     String subject,
                     String htmlContent,
                     boolean retryable) {

        BusinessEmailDispatch dispatch = BusinessEmailDispatch.builder()
                .organizationCode(organizationCode)
                .eventType(eventType)
                .recipientEmail(recipientEmail)
                .subject(subject)
                .htmlContent(htmlContent)
                .retryable(retryable)
                .status(BusinessEmailDispatchStatus.PENDING)
                .build();
        dispatchRepository.save(dispatch);

        try {
            mailService.sendEmail(recipientEmail, recipientName, subject, subject, htmlContent);
            dispatch.setStatus(BusinessEmailDispatchStatus.SENT);
            dispatch.setSentAt(Instant.now());
            log.info("[CriticalEmail] E-mail tipo={} enviado com sucesso para {}, dispatch={}",
                    eventType, recipientEmail, dispatch.getId());
        } catch (Exception e) {
            dispatch.setStatus(BusinessEmailDispatchStatus.FAILED);
            dispatch.setErrorMessage(e.getMessage());
            if (retryable) {
                log.error("[CriticalEmail] Falha ao enviar e-mail tipo={} para {} (será reprocessado pelo job): {}",
                        eventType, recipientEmail, e.getMessage());
            } else {
                log.error("[CriticalEmail] Falha ao enviar e-mail tipo={} para {} (não reprocessável — token pode expirar): {}",
                        eventType, recipientEmail, e.getMessage());
            }
        }
        dispatchRepository.save(dispatch);
    }
}
