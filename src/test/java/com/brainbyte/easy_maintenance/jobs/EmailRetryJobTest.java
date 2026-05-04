package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessEmailDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessEmailDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.EmailNotificationProvider;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessEmailDispatchRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailRetryJobTest {

    @Mock
    private BusinessEmailDispatchRepository dispatchRepository;

    @Mock
    private EmailNotificationProvider emailProvider;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @InjectMocks
    private EmailRetryJob job;

    @Test
    void shouldRetryFailedDispatchAndMarkAsSent() {
        BusinessEmailDispatch dispatch = failedDispatch(0);
        UserOrganization org = userOrgWith("Alice");
        when(dispatchRepository.findEligibleForRetry(
                eq(BusinessEmailDispatchStatus.FAILED), eq(EmailRetryJob.MAX_RETRIES), any(), any()))
                .thenReturn(List.of(dispatch));
        when(userOrganizationRepository.findAllByOrganizationCode("org-1"))
                .thenReturn(List.of(org));

        job.run();

        verify(emailProvider).send(any());
        ArgumentCaptor<BusinessEmailDispatch> saved = ArgumentCaptor.forClass(BusinessEmailDispatch.class);
        verify(dispatchRepository).save(saved.capture());

        BusinessEmailDispatch result = saved.getValue();
        assertThat(result.getStatus()).isEqualTo(BusinessEmailDispatchStatus.SENT);
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getLastRetryAt()).isNotNull();
    }

    @Test
    void shouldIncrementRetryCountOnFailureWithoutMarkingFinalUntilMaxReached() {
        BusinessEmailDispatch dispatch = failedDispatch(0);
        UserOrganization org = userOrgWith("Bob");
        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of(dispatch));
        when(userOrganizationRepository.findAllByOrganizationCode(any()))
                .thenReturn(List.of(org));
        doThrow(new RuntimeException("SMTP timeout")).when(emailProvider).send(any());

        job.run();

        ArgumentCaptor<BusinessEmailDispatch> saved = ArgumentCaptor.forClass(BusinessEmailDispatch.class);
        verify(dispatchRepository).save(saved.capture());

        BusinessEmailDispatch result = saved.getValue();
        assertThat(result.getRetryCount()).isEqualTo(1);
        assertThat(result.getStatus()).isEqualTo(BusinessEmailDispatchStatus.FAILED);
        assertThat(result.getLastRetryAt()).isNotNull();
        assertThat(result.getErrorMessage()).contains("SMTP timeout");
    }

    @Test
    void shouldDoNothingWhenNoEligibleDispatches() {
        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of());

        job.run();

        verify(emailProvider, never()).send(any());
        verify(dispatchRepository, never()).save(any());
    }

    @Test
    void shouldContinueProcessingRemainingDispatchesWhenOneFails() {
        BusinessEmailDispatch failDispatch = failedDispatch(0);
        BusinessEmailDispatch goodDispatch = failedDispatch(0);
        goodDispatch.setOrganizationCode("org-2");

        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of(failDispatch, goodDispatch));
        UserOrganization org = userOrgWith("User");
        when(userOrganizationRepository.findAllByOrganizationCode(any()))
                .thenReturn(List.of(org));
        doThrow(new RuntimeException("provider error"))
                .doNothing()
                .when(emailProvider).send(any());

        job.run();

        verify(dispatchRepository, times(2)).save(any());
    }

    @Test
    void shouldUseRecipientEmailAsNameFallbackWhenNoUserFound() {
        BusinessEmailDispatch dispatch = failedDispatch(0);
        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of(dispatch));
        when(userOrganizationRepository.findAllByOrganizationCode(any()))
                .thenReturn(List.of());

        job.run();

        verify(emailProvider).send(argThat(payload ->
                "user@test.com".equals(payload.getRecipientName())
        ));
    }

    @Test
    void shouldUseStoredHtmlContentForCriticalEmailRetry() {
        String storedSubject = "Renove sua assinatura - Easy Maintenance";
        String storedHtml = "<html><body>Trial expiring</body></html>";
        BusinessEmailDispatch dispatch = criticalFailedDispatch(0, NotificationEventType.TRIAL_EXPIRING,
                storedSubject, storedHtml);

        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of(dispatch));
        // no organizationCode → no user lookup needed

        job.run();

        verify(emailProvider).send(argThat(payload ->
                storedSubject.equals(payload.getSubject()) &&
                storedHtml.equals(payload.getHtmlContent())
        ));
        ArgumentCaptor<BusinessEmailDispatch> saved = ArgumentCaptor.forClass(BusinessEmailDispatch.class);
        verify(dispatchRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BusinessEmailDispatchStatus.SENT);
    }

    @Test
    void shouldMarkCriticalEmailAsFailedWhenProviderThrows() {
        BusinessEmailDispatch dispatch = criticalFailedDispatch(0, NotificationEventType.SUBSCRIPTION_BLOCKED,
                "Assinatura bloqueada", "<html>blocked</html>");
        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of(dispatch));
        doThrow(new RuntimeException("provider down")).when(emailProvider).send(any());

        job.run();

        ArgumentCaptor<BusinessEmailDispatch> saved = ArgumentCaptor.forClass(BusinessEmailDispatch.class);
        verify(dispatchRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(BusinessEmailDispatchStatus.FAILED);
        assertThat(saved.getValue().getErrorMessage()).contains("provider down");
    }

    @Test
    void shouldFallbackToEmailAsRecipientNameWhenOrgCodeIsNull() {
        BusinessEmailDispatch dispatch = criticalFailedDispatch(0, NotificationEventType.PAYMENT_PIX_OVERDUE,
                "PIX em atraso", "<html>pix</html>");

        when(dispatchRepository.findEligibleForRetry(any(), anyInt(), any(), any()))
                .thenReturn(List.of(dispatch));

        job.run();

        verify(emailProvider).send(argThat(payload ->
                "user@test.com".equals(payload.getRecipientName())
        ));
        verify(userOrganizationRepository, never()).findAllByOrganizationCode(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BusinessEmailDispatch failedDispatch(int retryCount) {
        return BusinessEmailDispatch.builder()
                .id(1L)
                .organizationCode("org-1")
                .eventType(NotificationEventType.ITEM_OVERDUE)
                .referenceType(NotificationReferenceType.ITEM)
                .referenceId(42L)
                .recipientEmail("user@test.com")
                .status(BusinessEmailDispatchStatus.FAILED)
                .retryable(true)
                .retryCount(retryCount)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
    }

    private BusinessEmailDispatch criticalFailedDispatch(int retryCount, NotificationEventType eventType,
                                                          String subject, String htmlContent) {
        return BusinessEmailDispatch.builder()
                .id(2L)
                .organizationCode(null)
                .eventType(eventType)
                .recipientEmail("user@test.com")
                .subject(subject)
                .htmlContent(htmlContent)
                .status(BusinessEmailDispatchStatus.FAILED)
                .retryable(true)
                .retryCount(retryCount)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();
    }

    private UserOrganization userOrgWith(String name) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(name);
        UserOrganization uo = mock(UserOrganization.class);
        when(uo.getUser()).thenReturn(user);
        return uo;
    }
}
