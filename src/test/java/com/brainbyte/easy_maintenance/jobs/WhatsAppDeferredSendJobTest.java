package com.brainbyte.easy_maintenance.jobs;

import com.brainbyte.easy_maintenance.infrastructure.notification.domain.BusinessWhatsAppDispatch;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.BusinessWhatsAppDispatchStatus;
import com.brainbyte.easy_maintenance.infrastructure.notification.repository.BusinessWhatsAppDispatchRepository;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.BusinessWhatsAppNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppDeferredSendJobTest {

    @Mock BusinessWhatsAppDispatchRepository dispatchRepository;
    @Mock BusinessWhatsAppNotificationService whatsAppNotificationService;

    @InjectMocks WhatsAppDeferredSendJob job;

    @Test
    void run_doesNothing_whenOutsideBusinessHours() {
        when(whatsAppNotificationService.isWithinBusinessHours()).thenReturn(false);

        job.run();

        verifyNoInteractions(dispatchRepository);
    }

    @Test
    void run_doesNothing_whenNoPendingDispatches() {
        when(whatsAppNotificationService.isWithinBusinessHours()).thenReturn(true);
        when(dispatchRepository.findAllByStatus(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW))
                .thenReturn(List.of());

        job.run();

        verify(whatsAppNotificationService, never()).attemptSend(any());
    }

    @Test
    void run_attemptsSendForEachPendingDispatch() {
        when(whatsAppNotificationService.isWithinBusinessHours()).thenReturn(true);
        BusinessWhatsAppDispatch d1 = BusinessWhatsAppDispatch.builder().id(1L).build();
        BusinessWhatsAppDispatch d2 = BusinessWhatsAppDispatch.builder().id(2L).build();
        when(dispatchRepository.findAllByStatus(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW))
                .thenReturn(List.of(d1, d2));

        job.run();

        verify(whatsAppNotificationService).attemptSend(d1);
        verify(whatsAppNotificationService).attemptSend(d2);
    }

    @Test
    void run_continuesProcessingRemainingDispatches_whenOneThrows() {
        when(whatsAppNotificationService.isWithinBusinessHours()).thenReturn(true);
        BusinessWhatsAppDispatch d1 = BusinessWhatsAppDispatch.builder().id(1L).build();
        BusinessWhatsAppDispatch d2 = BusinessWhatsAppDispatch.builder().id(2L).build();
        when(dispatchRepository.findAllByStatus(BusinessWhatsAppDispatchStatus.PENDING_HOURS_WINDOW))
                .thenReturn(List.of(d1, d2));
        doThrow(new RuntimeException("boom")).when(whatsAppNotificationService).attemptSend(d1);

        job.run();

        verify(whatsAppNotificationService).attemptSend(d1);
        verify(whatsAppNotificationService).attemptSend(d2);
    }
}
