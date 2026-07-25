package com.brainbyte.easy_maintenance.jobs.infrastucture.web;

import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.mail.MailerSendServiceImpl;
import com.brainbyte.easy_maintenance.infrastructure.mail.ResendServiceImpl;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.BusinessWhatsAppNotificationService;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.NotificationEventDetectionService;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.NotificationOrchestratorService;
import com.brainbyte.easy_maintenance.jobs.service.TrialExpirationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock TrialExpirationService trialExpirationService;
    @Mock NotificationEventDetectionService detectionService;
    @Mock NotificationOrchestratorService orchestratorService;
    @Mock BusinessWhatsAppDispatchRepository dispatchRepository;
    @Mock BusinessWhatsAppNotificationService whatsAppNotificationService;
    @Mock ResendServiceImpl resendService;
    @Mock MailerSendServiceImpl mailerSendService;

    private JobController controller(Optional<ResendServiceImpl> resend, Optional<MailerSendServiceImpl> mailerSend) {
        return new JobController(trialExpirationService, detectionService, orchestratorService,
                dispatchRepository, whatsAppNotificationService, resend, mailerSend);
    }

    private JobController controllerWithBothProvidersAvailable() {
        return controller(Optional.of(resendService), Optional.of(mailerSendService));
    }

    @Test
    void testEmailSend_defaultsToResendWhenProviderOmitted() {
        Map<String, Object> result = controllerWithBothProvidersAvailable().testEmailSend("cliente@teste.com", "resend");

        verify(resendService).sendEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(mailerSendService);
        assertThat(result).containsEntry("provider", "resend").containsEntry("sentTo", "cliente@teste.com");
    }

    @Test
    void testEmailSend_withMailerSendProvider_callsMailerSend() {
        Map<String, Object> result = controllerWithBothProvidersAvailable().testEmailSend("cliente@teste.com", "mailersend");

        verify(mailerSendService).sendEmail(anyString(), anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(resendService);
        assertThat(result).containsEntry("provider", "mailersend");
    }

    @Test
    void testEmailSend_providerIsCaseInsensitive() {
        controllerWithBothProvidersAvailable().testEmailSend("cliente@teste.com", "MailerSend");

        verify(mailerSendService).sendEmail(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testEmailSend_withUnknownProvider_throwsRuleExceptionWithoutSendingAnything() {
        JobController controller = controllerWithBothProvidersAvailable();

        assertThatThrownBy(() -> controller.testEmailSend("cliente@teste.com", "sendgrid"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("sendgrid");

        verifyNoInteractions(resendService, mailerSendService);
    }

    // Reproduz o boot local real: perfil "local" não registra ResendServiceImpl/MailerSendServiceImpl
    // (só MailHog) — o controller precisa continuar sendo construído normalmente (Optional vazio),
    // e só falhar (com erro claro) se o endpoint for de fato chamado pedindo um provedor ausente.
    @Test
    void testEmailSend_whenResendBeanNotAvailable_throwsClearRuleExceptionInsteadOfNpe() {
        JobController controller = controller(Optional.empty(), Optional.of(mailerSendService));

        assertThatThrownBy(() -> controller.testEmailSend("cliente@teste.com", "resend"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("resend");

        verifyNoInteractions(mailerSendService);
    }

    @Test
    void testEmailSend_whenMailerSendBeanNotAvailable_throwsClearRuleException() {
        JobController controller = controller(Optional.of(resendService), Optional.empty());

        assertThatThrownBy(() -> controller.testEmailSend("cliente@teste.com", "mailersend"))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("mailersend");

        verifyNoInteractions(resendService);
    }
}
