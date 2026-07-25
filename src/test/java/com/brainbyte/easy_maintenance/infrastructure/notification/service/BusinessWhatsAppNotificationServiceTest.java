package com.brainbyte.easy_maintenance.infrastructure.notification.service;

import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppPermanentException;
import com.brainbyte.easy_maintenance.commons.exceptions.WhatsAppTransientException;
import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationEvent;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.WhatsAppSendResult;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationChannel;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationReferenceType;
import com.brainbyte.easy_maintenance.infrastructure.notification.dto.NotificationPayload;
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.WhatsAppNotificationProvider;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRecipient;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessWhatsAppNotificationServiceTest {

    // 2026-07-20T15:00:00Z == 12:00 em America/Sao_Paulo (UTC-3) — dentro do horário comercial.
    private static final Instant WITHIN_BUSINESS_HOURS = Instant.parse("2026-07-20T15:00:00Z");
    // 2026-07-20T05:00:00Z == 02:00 em America/Sao_Paulo — fora do horário comercial.
    private static final Instant OUTSIDE_BUSINESS_HOURS = Instant.parse("2026-07-20T05:00:00Z");

    @Mock WhatsAppNotificationProvider whatsAppProvider;
    @Mock BusinessWhatsAppDispatchRepository dispatchRepository;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock BusinessEmailNotificationService emailNotificationService;
    @Mock BusinessWhatsAppQuotaService quotaService;

    private BusinessWhatsAppNotificationService service;

    private static final String ORG = "ORG-001";

    @BeforeEach
    void setUp() {
        service = new BusinessWhatsAppNotificationService(
                whatsAppProvider, dispatchRepository, userOrganizationRepository,
                emailNotificationService, quotaService, Clock.fixed(WITHIN_BUSINESS_HOURS, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "fallbackToEmailEnabled", true);
        ReflectionTestUtils.setField(service, "dailyLimitPerRecipient", 3);
        ReflectionTestUtils.setField(service, "businessHoursStart", 8);
        ReflectionTestUtils.setField(service, "businessHoursEnd", 20);
        lenientDefaults();
    }

    private void lenientDefaults() {
        lenient().when(dispatchRepository
                        .findByOrganizationCodeAndEventTypeAndReferenceTypeAndReferenceIdAndDueDateAndDaysOffset(
                                any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(dispatchRepository.save(any(BusinessWhatsAppDispatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(quotaService.canSend(anyString())).thenReturn(true);
        lenient().when(dispatchRepository.countSentToPhoneSince(anyString(), any(Instant.class))).thenReturn(0L);
    }

    private NotificationEvent event(NotificationEventType type, int daysOffset) {
        return NotificationEvent.builder()
                .organizationCode(ORG)
                .eventType(type)
                .referenceType(NotificationReferenceType.ITEM)
                .referenceId(1L)
                .referenceLabel("Extintor")
                .dueDate(LocalDate.now().plusDays(daysOffset))
                .daysOffset(daysOffset)
                .build();
    }

    private User user(String phoneNumber, boolean optIn) {
        return User.builder().id(1L).name("João").email("joao@test.com")
                .phoneNumber(phoneNumber).whatsappOptIn(optIn).build();
    }

    private void stubRecipient(User user) {
        stubRecipient(user, "Empresa Teste");
    }

    private void stubRecipient(User user, String organizationName) {
        when(userOrganizationRepository.findRecipientsWithOrganizationName(ORG))
                .thenReturn(List.of(new UserOrganizationRecipient(user, organizationName)));
    }

    private void stubNoRecipient() {
        when(userOrganizationRepository.findRecipientsWithOrganizationName(ORG)).thenReturn(List.of());
    }

    // ── destinatário / opt-in / telefone ────────────────────────────────────

    @Test
    void sendWhatsapp_skipsWhenNoRecipientFound() {
        stubNoRecipient();

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SKIPPED_INVALID_RECIPIENT);
    }

    @Test
    void sendWhatsapp_skipsWhenUserHasNotOptedIn() {
        stubRecipient(user("+5531972139145", false));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SKIPPED_OPT_OUT);
    }

    @Test
    void sendWhatsapp_skipsWhenPhoneNumberIsMissing() {
        stubRecipient(user(null, true));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SKIPPED_INVALID_RECIPIENT);
    }

    @Test
    void sendWhatsapp_skipsWhenPhoneNumberIsInvalid() {
        stubRecipient(user("123", true));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
    }

    // ── sucesso ──────────────────────────────────────────────────────────────

    @Test
    void sendWhatsapp_onSuccess_persistsWamidAndStatusSent() {
        stubRecipient(user("+5531972139145", true));
        when(whatsAppProvider.sendTemplateMessage(any())).thenReturn(new WhatsAppSendResult("wamid.123"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SENT);
        assertThat(captor.getValue().getWamid()).isEqualTo("wamid.123");
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void sendWhatsapp_buildsPayloadWithCompanyNameAndItemId() {
        stubRecipient(user("+5531972139145", true), "Empresa Teste LTDA");
        when(whatsAppProvider.sendTemplateMessage(any())).thenReturn(new WhatsAppSendResult("wamid.123"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(whatsAppProvider).sendTemplateMessage(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getTemplateData())
                .containsEntry("companyName", "Empresa Teste LTDA")
                .containsEntry("itemId", "1");
    }

    // ── idempotência ─────────────────────────────────────────────────────────

    @Test
    void sendWhatsapp_skipsWhenAlreadySentForSameKey() {
        BusinessWhatsAppDispatch alreadySent = BusinessWhatsAppDispatch.builder()
                .status(BusinessWhatsAppDispatchStatus.SENT)
                .wamid("wamid.existing")
                .build();
        when(dispatchRepository.findByOrganizationCodeAndEventTypeAndReferenceTypeAndReferenceIdAndDueDateAndDaysOffset(
                any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(Optional.of(alreadySent));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        verifyNoInteractions(userOrganizationRepository);
        verify(dispatchRepository, never()).save(any());
    }

    // ── fallback para e-mail ──────────────────────────────────────────────────

    @Test
    void sendWhatsapp_onPermanentFailure_triggersEmailFallbackWhenEmailNotAlreadyResolved() {
        stubRecipient(user("+5531972139145", true));
        when(whatsAppProvider.sendTemplateMessage(any())).thenThrow(new WhatsAppPermanentException("template inválido"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_NEAR_DUE, 1), Set.of(NotificationChannel.WHATSAPP, NotificationChannel.PUSH));

        verify(emailNotificationService).sendEmail(any());
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.FAILED);
    }

    @Test
    void sendWhatsapp_onPermanentFailure_doesNotDuplicateEmailWhenAlreadyResolved() {
        stubRecipient(user("+5531972139145", true));
        when(whatsAppProvider.sendTemplateMessage(any())).thenThrow(new WhatsAppPermanentException("template inválido"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0),
                EnumSet.of(NotificationChannel.WHATSAPP, NotificationChannel.EMAIL, NotificationChannel.PUSH));

        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void sendWhatsapp_onTransientFailure_alsoTriggersEmailFallback() {
        stubRecipient(user("+5531972139145", true));
        when(whatsAppProvider.sendTemplateMessage(any()))
                .thenThrow(new WhatsAppTransientException("falha transitória esgotou retries"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_NEAR_DUE, 1), Set.of(NotificationChannel.WHATSAPP, NotificationChannel.PUSH));

        verify(emailNotificationService).sendEmail(any());
    }

    @Test
    void sendWhatsapp_fallbackDisabled_doesNotTriggerEmailEvenOnPermanentFailure() {
        ReflectionTestUtils.setField(service, "fallbackToEmailEnabled", false);
        stubRecipient(user("+5531972139145", true));
        when(whatsAppProvider.sendTemplateMessage(any())).thenThrow(new WhatsAppPermanentException("template inválido"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_NEAR_DUE, 1), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(emailNotificationService);
    }

    // ── quota mensal (TASK-131) ────────────────────────────────────────────────

    @Test
    void sendWhatsapp_skipsWhenMonthlyQuotaExceeded() {
        stubRecipient(user("+5531972139145", true));
        when(quotaService.canSend(ORG)).thenReturn(false);

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SKIPPED_QUOTA);
    }

    // ── rate limit diário (TASK-131) ───────────────────────────────────────────

    @Test
    void sendWhatsapp_skipsWhenDailyRateLimitReached() {
        stubRecipient(user("+5531972139145", true));
        when(dispatchRepository.countSentToPhoneSince(eq("+5531972139145"), any(Instant.class))).thenReturn(3L);

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SKIPPED_RATE_LIMIT);
    }

    @Test
    void sendWhatsapp_allowsSendingBelowDailyRateLimit() {
        stubRecipient(user("+5531972139145", true));
        when(dispatchRepository.countSentToPhoneSince(eq("+5531972139145"), any(Instant.class))).thenReturn(2L);
        when(whatsAppProvider.sendTemplateMessage(any())).thenReturn(new WhatsAppSendResult("wamid.1"));

        service.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verify(whatsAppProvider).sendTemplateMessage(any());
    }

    // ── horário comercial (TASK-131) ───────────────────────────────────────────

    @Test
    void sendWhatsapp_outsideBusinessHours_defersInsteadOfSending() {
        BusinessWhatsAppNotificationService serviceOutsideHours = new BusinessWhatsAppNotificationService(
                whatsAppProvider, dispatchRepository, userOrganizationRepository,
                emailNotificationService, quotaService, Clock.fixed(OUTSIDE_BUSINESS_HOURS, ZoneOffset.UTC));
        ReflectionTestUtils.setField(serviceOutsideHours, "fallbackToEmailEnabled", true);
        ReflectionTestUtils.setField(serviceOutsideHours, "dailyLimitPerRecipient", 3);
        ReflectionTestUtils.setField(serviceOutsideHours, "businessHoursStart", 8);
        ReflectionTestUtils.setField(serviceOutsideHours, "businessHoursEnd", 20);

        stubRecipient(user("+5531972139145", true));

        serviceOutsideHours.sendWhatsapp(event(NotificationEventType.ITEM_OVERDUE, 0), Set.of(NotificationChannel.WHATSAPP));

        verifyNoInteractions(whatsAppProvider);
        ArgumentCaptor<BusinessWhatsAppDispatch> captor = ArgumentCaptor.forClass(BusinessWhatsAppDispatch.class);
        verify(dispatchRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW);
    }

    @Test
    void isWithinBusinessHours_reflectsInjectedClock() {
        assertThat(service.isWithinBusinessHours()).isTrue();

        BusinessWhatsAppNotificationService serviceOutsideHours = new BusinessWhatsAppNotificationService(
                whatsAppProvider, dispatchRepository, userOrganizationRepository,
                emailNotificationService, quotaService, Clock.fixed(OUTSIDE_BUSINESS_HOURS, ZoneOffset.UTC));
        ReflectionTestUtils.setField(serviceOutsideHours, "businessHoursStart", 8);
        ReflectionTestUtils.setField(serviceOutsideHours, "businessHoursEnd", 20);

        assertThat(serviceOutsideHours.isWithinBusinessHours()).isFalse();
    }

    // ── attemptSend (flush do WhatsAppDeferredSendJob) ─────────────────────────

    @Test
    void attemptSend_reChecksOptOut_evenForAlreadyQueuedDispatch() {
        // Usuário deu opt-out DEPOIS que a mensagem já tinha sido represada (PENDING_HOURS_WINDOW).
        stubRecipient(user("+5531972139145", false));

        BusinessWhatsAppDispatch queued = BusinessWhatsAppDispatch.builder()
                .organizationCode(ORG)
                .status(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW)
                .build();

        service.attemptSend(queued);

        verifyNoInteractions(whatsAppProvider);
        assertThat(queued.getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SKIPPED_OPT_OUT);
    }

    @Test
    void attemptSend_sendsSuccessfullyWhenNowWithinHours() {
        stubRecipient(user("+5531972139145", true));
        when(whatsAppProvider.sendTemplateMessage(any())).thenReturn(new WhatsAppSendResult("wamid.deferred"));

        BusinessWhatsAppDispatch queued = BusinessWhatsAppDispatch.builder()
                .organizationCode(ORG)
                .eventType(NotificationEventType.ITEM_OVERDUE)
                .referenceType(NotificationReferenceType.ITEM)
                .referenceId(1L)
                .referenceLabel("Extintor")
                .dueDate(LocalDate.now())
                .daysOffset(0)
                .status(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW)
                .build();

        service.attemptSend(queued);

        assertThat(queued.getStatus()).isEqualTo(BusinessWhatsAppDispatchStatus.SENT);
        assertThat(queued.getWamid()).isEqualTo("wamid.deferred");
    }
}
