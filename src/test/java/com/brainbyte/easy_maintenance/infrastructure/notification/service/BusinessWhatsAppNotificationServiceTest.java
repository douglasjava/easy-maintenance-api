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
import com.brainbyte.easy_maintenance.infrastructure.notification.provider.WhatsAppNotificationProvider;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessWhatsAppNotificationServiceTest {

    @Mock WhatsAppNotificationProvider whatsAppProvider;
    @Mock BusinessWhatsAppDispatchRepository dispatchRepository;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock BusinessEmailNotificationService emailNotificationService;

    private BusinessWhatsAppNotificationService service;

    private static final String ORG = "ORG-001";

    @BeforeEach
    void setUp() {
        service = new BusinessWhatsAppNotificationService(
                whatsAppProvider, dispatchRepository, userOrganizationRepository, emailNotificationService);
        ReflectionTestUtils.setField(service, "fallbackToEmailEnabled", true);
        lenientNoExistingDispatch();
    }

    private void lenientNoExistingDispatch() {
        lenient().when(dispatchRepository
                        .findByOrganizationCodeAndEventTypeAndReferenceTypeAndReferenceIdAndDueDateAndDaysOffset(
                                any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(Optional.empty());
        lenient().when(dispatchRepository.save(any(BusinessWhatsAppDispatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
        UserOrganization uo = UserOrganization.builder().user(user).organizationCode(ORG).build();
        when(userOrganizationRepository.findAllByOrganizationCodeWithUser(ORG)).thenReturn(List.of(uo));
    }

    private void stubNoRecipient() {
        when(userOrganizationRepository.findAllByOrganizationCodeWithUser(ORG)).thenReturn(List.of());
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
}
