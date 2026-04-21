package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.infrastructure.mail.MailService;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.InAppNotificationType;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.InAppNotificationService;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingNotificationService {

    private final MailService mailService;
    private final EmailTemplateHelper emailTemplateHelper;
    private final OrganizationRepository organizationRepository;
    private final InAppNotificationService inAppNotificationService;

    public void sendCancellationProcessedEmail(BillingSubscription subscription) {
        User user = subscription.getBillingAccount().getUser();
        String recipientEmail = subscription.getBillingAccount().getBillingEmail();
        
        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = user.getEmail();
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[BillingNotification] Destinatário sem e-mail válido para cancelamento. Subscription ID: {}", subscription.getId());
            return;
        }

        try {
            log.info("[BillingNotification] Iniciando envio de e-mail de cancelamento para {}", recipientEmail);
            
            String userName = user.getName() != null ? user.getName() : "Usuário";
            String organizationName = getOrganizationName(user);
            
            String subject = "Cancelamento da assinatura realizado";
            String htmlContent = emailTemplateHelper.generateCancellationProcessedHtml(userName, organizationName);
            
            mailService.sendEmail(recipientEmail, userName, subject, subject, htmlContent);
            log.info("[BillingNotification] E-mail de cancelamento enviado com sucesso para {}", recipientEmail);
        } catch (Exception e) {
            log.error("[BillingNotification] Falha ao enviar e-mail de cancelamento para {}: {}", recipientEmail, e.getMessage());
        }
    }

    public void sendSubscriptionBlockedEmail(BillingSubscription subscription) {
        User user = subscription.getBillingAccount().getUser();
        String recipientEmail = subscription.getBillingAccount().getBillingEmail();

        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = user.getEmail();
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[BillingNotification] Destinatário sem e-mail válido para bloqueio. Subscription ID: {}", subscription.getId());
            return;
        }

        try {
            log.info("[BillingNotification] Iniciando envio de e-mail de bloqueio para {}", recipientEmail);

            String userName = user.getName() != null ? user.getName() : "Usuário";
            String organizationName = getOrganizationName(user);

            String subject = "Assinatura bloqueada no Easy Maintenance";
            String htmlContent = emailTemplateHelper.generateSubscriptionBlockedHtml(userName, organizationName);

            mailService.sendEmail(recipientEmail, userName, subject, subject, htmlContent);
            log.info("[BillingNotification] E-mail de bloqueio enviado com sucesso para {}", recipientEmail);
        } catch (Exception e) {
            log.error("[BillingNotification] Falha ao enviar e-mail de bloqueio para {}: {}", recipientEmail, e.getMessage());
        }

        try {
            inAppNotificationService.saveForUser(
                    user.getId(),
                    "Assinatura bloqueada",
                    "Sua assinatura foi bloqueada por falta de pagamento. Regularize para continuar usando o Easy Maintenance.",
                    InAppNotificationType.SUBSCRIPTION_BLOCKED,
                    null
            );
        } catch (Exception e) {
            log.error("[BillingNotification] Falha ao salvar notificação in-app de bloqueio para usuário {}: {}", user.getId(), e.getMessage());
        }
    }

    public void sendPixOverdueEmail(Payment payment) {
        User user = payment.getPayer();
        String recipientEmail = null;

        var subscription = payment.getBillingSubscription();
        if (subscription != null && subscription.getBillingAccount() != null) {
            recipientEmail = subscription.getBillingAccount().getBillingEmail();
        }
        if (recipientEmail == null || recipientEmail.isBlank()) {
            recipientEmail = user.getEmail();
        }

        if (recipientEmail == null || recipientEmail.isBlank()) {
            log.warn("[BillingNotification] Destinatário sem e-mail válido para PIX overdue. Payment ID: {}", payment.getId());
            return;
        }

        try {
            log.info("[BillingNotification] Enviando e-mail de PIX em atraso para {}", recipientEmail);

            String userName = user.getName() != null ? user.getName() : "Usuário";
            String amountFormatted = payment.getAmountCents() != null
                    ? "R$ " + String.format("%.2f", payment.getAmountCents() / 100.0)
                    : "valor pendente";
            String paymentLink = payment.getPaymentLink() != null ? payment.getPaymentLink() : "";

            String subject = "Pagamento PIX em atraso — Easy Maintenance";
            String htmlContent = emailTemplateHelper.generatePixOverdueHtml(userName, amountFormatted, paymentLink);

            mailService.sendEmail(recipientEmail, userName, subject, subject, htmlContent);
            log.info("[BillingNotification] E-mail de PIX em atraso enviado com sucesso para {}", recipientEmail);
        } catch (Exception e) {
            log.error("[BillingNotification] Falha ao enviar e-mail de PIX em atraso para {}: {}", recipientEmail, e.getMessage());
        }

        try {
            inAppNotificationService.saveForUser(
                    user.getId(),
                    "Pagamento PIX em atraso",
                    "Seu pagamento via PIX está em atraso. Acesse o billing para regularizar e evitar o bloqueio do seu acesso.",
                    InAppNotificationType.SUBSCRIPTION_BLOCKED,
                    null
            );
        } catch (Exception e) {
            log.error("[BillingNotification] Falha ao salvar notificação in-app de PIX overdue para usuário {}: {}", user.getId(), e.getMessage());
        }
    }

    private String getOrganizationName(User user) {
        try {
            List<Organization> organizations = organizationRepository.findAllByUserId(user.getId());
            if (!organizations.isEmpty()) {
                return organizations.get(0).getName();
            }
        } catch (Exception e) {
            log.warn("[BillingNotification] Erro ao buscar nome da organização para o usuário {}: {}", user.getId(), e.getMessage());
        }
        return "sua organização";
    }
}
