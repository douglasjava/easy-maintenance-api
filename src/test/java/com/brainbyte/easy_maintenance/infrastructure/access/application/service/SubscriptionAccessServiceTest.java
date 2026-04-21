package com.brainbyte.easy_maintenance.infrastructure.access.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.infrastructure.access.domain.enums.AccessMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionAccessServiceTest {

    @Mock
    private BillingSubscriptionItemRepository subscriptionItemRepository;

    @InjectMocks
    private SubscriptionAccessService service;

    // -----------------------------------------------------------------------
    // resolveUserAccessMode — mapeamento status → AccessMode
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnFullAccessForActiveUser() {
        mockUserSubscription(1L, SubscriptionStatus.ACTIVE);
        assertEquals(AccessMode.FULL_ACCESS, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnFullAccessForActiveTrialUser() {
        Instant futureDate = Instant.now().plus(7, ChronoUnit.DAYS);
        mockUserSubscription(1L, SubscriptionStatus.TRIAL, futureDate);
        assertEquals(AccessMode.FULL_ACCESS, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForExpiredTrialUser() {
        Instant pastDate = Instant.now().minus(1, ChronoUnit.DAYS);
        mockUserSubscription(1L, SubscriptionStatus.TRIAL, pastDate);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnFullAccessForTrialUserWithNullPeriodEnd() {
        mockUserSubscription(1L, SubscriptionStatus.TRIAL, null);
        assertEquals(AccessMode.FULL_ACCESS, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnNoAccessForBlockedUser() {
        mockUserSubscription(1L, SubscriptionStatus.BLOCKED);
        assertEquals(AccessMode.NO_ACCESS, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForPastDueUser() {
        mockUserSubscription(1L, SubscriptionStatus.PAST_DUE);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForCanceledUser() {
        mockUserSubscription(1L, SubscriptionStatus.CANCELED);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForPendingPaymentUser() {
        mockUserSubscription(1L, SubscriptionStatus.PENDING_PAYMENT);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForPaymentFailedUser() {
        mockUserSubscription(1L, SubscriptionStatus.PAYMENT_FAILED);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForNoneStatusUser() {
        mockUserSubscription(1L, SubscriptionStatus.NONE);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyForPendingActivationUser() {
        mockUserSubscription(1L, SubscriptionStatus.PENDING_ACTIVATION);
        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(1L));
    }

    @Test
    void shouldReturnReadOnlyWhenUserHasNoSubscription() {
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), any()))
                .thenReturn(List.of());

        assertEquals(AccessMode.READ_ONLY, service.resolveUserAccessMode(99L));
    }

    // -----------------------------------------------------------------------
    // resolveOrganizationAccessMode
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnFullAccessForActiveOrganization() {
        mockOrgSubscription("ORG-001", SubscriptionStatus.ACTIVE);
        assertEquals(AccessMode.FULL_ACCESS, service.resolveOrganizationAccessMode("ORG-001"));
    }

    @Test
    void shouldReturnNoAccessForBlockedOrganization() {
        mockOrgSubscription("ORG-001", SubscriptionStatus.BLOCKED);
        assertEquals(AccessMode.NO_ACCESS, service.resolveOrganizationAccessMode("ORG-001"));
    }

    @Test
    void shouldReturnReadOnlyWhenOrganizationHasNoSubscription() {
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), any()))
                .thenReturn(List.of());

        assertEquals(AccessMode.READ_ONLY, service.resolveOrganizationAccessMode("ORG-NONE"));
    }

    // -----------------------------------------------------------------------
    // resolveEffectiveStatus — static helper
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnTrialExpiredWhenTrialPeriodHasPassed() {
        Instant pastDate = Instant.now().minus(1, ChronoUnit.DAYS);
        SubscriptionStatus result = SubscriptionAccessService.resolveEffectiveStatus(SubscriptionStatus.TRIAL, pastDate);
        assertEquals(SubscriptionStatus.TRIAL_EXPIRED, result);
    }

    @Test
    void shouldReturnTrialWhenTrialPeriodIsInFuture() {
        Instant futureDate = Instant.now().plus(7, ChronoUnit.DAYS);
        SubscriptionStatus result = SubscriptionAccessService.resolveEffectiveStatus(SubscriptionStatus.TRIAL, futureDate);
        assertEquals(SubscriptionStatus.TRIAL, result);
    }

    @Test
    void shouldReturnTrialWhenPeriodEndIsNull() {
        SubscriptionStatus result = SubscriptionAccessService.resolveEffectiveStatus(SubscriptionStatus.TRIAL, null);
        assertEquals(SubscriptionStatus.TRIAL, result);
    }

    @Test
    void shouldNotAffectNonTrialStatuses() {
        Instant pastDate = Instant.now().minus(1, ChronoUnit.DAYS);
        assertEquals(SubscriptionStatus.ACTIVE,
                SubscriptionAccessService.resolveEffectiveStatus(SubscriptionStatus.ACTIVE, pastDate));
        assertEquals(SubscriptionStatus.BLOCKED,
                SubscriptionAccessService.resolveEffectiveStatus(SubscriptionStatus.BLOCKED, pastDate));
        assertEquals(SubscriptionStatus.CANCELED,
                SubscriptionAccessService.resolveEffectiveStatus(SubscriptionStatus.CANCELED, pastDate));
    }

    @Test
    void shouldHandleNullStatus() {
        assertNull(SubscriptionAccessService.resolveEffectiveStatus(null, Instant.now()));
    }

    // -----------------------------------------------------------------------
    // getUserSubscriptionStatus / getOrganizationSubscriptionStatus
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnPresentStatusForExistingUser() {
        mockUserSubscription(1L, SubscriptionStatus.TRIAL);
        Optional<SubscriptionStatus> status = service.getUserSubscriptionStatus(1L);
        assertTrue(status.isPresent());
        assertEquals(SubscriptionStatus.TRIAL, status.get());
    }

    @Test
    void shouldReturnEmptyStatusForUserWithNoSubscription() {
        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), any()))
                .thenReturn(List.of());

        Optional<SubscriptionStatus> status = service.getUserSubscriptionStatus(42L);
        assertFalse(status.isPresent());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void mockUserSubscription(Long userId, SubscriptionStatus status) {
        mockUserSubscription(userId, status, null);
    }

    private void mockUserSubscription(Long userId, SubscriptionStatus status, Instant periodEnd) {
        BillingSubscription subscription = mock(BillingSubscription.class);
        when(subscription.getStatus()).thenReturn(status);
        // Use lenient to avoid UnnecessaryStubbingException in tests that don't call getCurrentPeriodEnd()
        lenient().when(subscription.getCurrentPeriodEnd()).thenReturn(periodEnd);

        BillingSubscriptionItem item = mock(BillingSubscriptionItem.class);
        when(item.getBillingSubscription()).thenReturn(subscription);

        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.USER), eq(List.of(userId.toString()))))
                .thenReturn(List.of(item));
    }

    private void mockOrgSubscription(String orgCode, SubscriptionStatus status) {
        BillingSubscription subscription = mock(BillingSubscription.class);
        when(subscription.getStatus()).thenReturn(status);
        lenient().when(subscription.getCurrentPeriodEnd()).thenReturn(null);

        BillingSubscriptionItem item = mock(BillingSubscriptionItem.class);
        when(item.getBillingSubscription()).thenReturn(subscription);

        when(subscriptionItemRepository.findAllBySourceTypeAndSourceIdIn(
                eq(BillingSubscriptionItemSourceType.ORGANIZATION), eq(List.of(orgCode))))
                .thenReturn(List.of(item));
    }
}
