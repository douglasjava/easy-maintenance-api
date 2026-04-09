package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingNotificationService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionBlockingServiceTest {

    @Mock
    private BillingSubscriptionRepository billingSubscriptionRepository;

    @Mock
    private BillingNotificationService billingNotificationService;

    @InjectMocks
    private SubscriptionBlockingService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "daysAfterDue", 3);
    }

    @Test
    void shouldBlockEligiblePastDueSubscriptions() {
        BillingSubscription sub = buildPastDueSubscription();
        when(billingSubscriptionRepository.findEligibleForBlocking(any())).thenReturn(List.of(sub));
        when(billingSubscriptionRepository.save(sub)).thenReturn(sub);

        service.executeBlockingJob();

        verify(billingSubscriptionRepository).save(sub);
        verify(billingNotificationService).sendSubscriptionBlockedEmail(sub);
        assertEquals(SubscriptionStatus.BLOCKED, sub.getStatus());
    }

    @Test
    void shouldSkipAlreadyBlockedSubscriptions() {
        BillingSubscription sub = buildSubscriptionWithStatus(SubscriptionStatus.BLOCKED);
        when(billingSubscriptionRepository.findEligibleForBlocking(any())).thenReturn(List.of(sub));

        service.executeBlockingJob();

        verify(billingSubscriptionRepository, never()).save(any());
        verify(billingNotificationService, never()).sendSubscriptionBlockedEmail(any());
    }

    @Test
    void shouldProcessRemainingSubscriptionsWhenOneFails() {
        BillingSubscription failSub = mock(BillingSubscription.class);
        when(failSub.getStatus()).thenReturn(SubscriptionStatus.PAST_DUE);
        when(failSub.getId()).thenReturn(1L);
        doThrow(new RuntimeException("DB error")).when(failSub).block();

        // Mock the account chain for logging
        BillingAccount account = mock(BillingAccount.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(account.getUser()).thenReturn(user);
        when(failSub.getBillingAccount()).thenReturn(account);

        BillingSubscription goodSub = buildPastDueSubscription();
        when(billingSubscriptionRepository.findEligibleForBlocking(any()))
                .thenReturn(List.of(failSub, goodSub));
        when(billingSubscriptionRepository.save(goodSub)).thenReturn(goodSub);

        assertDoesNotThrow(() -> service.executeBlockingJob(),
                "Job deve continuar processando mesmo quando uma assinatura falha");

        verify(billingSubscriptionRepository).save(goodSub);
    }

    @Test
    void shouldDoNothingWhenNoEligibleSubscriptions() {
        when(billingSubscriptionRepository.findEligibleForBlocking(any())).thenReturn(List.of());

        service.executeBlockingJob();

        verify(billingSubscriptionRepository, never()).save(any());
        verify(billingNotificationService, never()).sendSubscriptionBlockedEmail(any());
    }

    @Test
    void shouldBlockMultipleEligibleSubscriptions() {
        BillingSubscription sub1 = buildPastDueSubscription();
        BillingSubscription sub2 = buildPastDueSubscription();
        when(billingSubscriptionRepository.findEligibleForBlocking(any())).thenReturn(List.of(sub1, sub2));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.executeBlockingJob();

        verify(billingSubscriptionRepository, times(2)).save(any());
        verify(billingNotificationService, times(2)).sendSubscriptionBlockedEmail(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BillingSubscription buildPastDueSubscription() {
        BillingSubscription sub = buildSubscriptionWithStatus(SubscriptionStatus.PAST_DUE);
        // Mock account chain for logging
        BillingAccount account = mock(BillingAccount.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(account.getUser()).thenReturn(user);
        sub.setBillingAccount(account);
        return sub;
    }

    private BillingSubscription buildSubscriptionWithStatus(SubscriptionStatus status) {
        return BillingSubscription.builder()
                .status(status)
                .build();
    }
}
