package com.brainbyte.easy_maintenance.billing.domain;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class BillingSubscriptionTest {

    @Test
    void createTrial_shouldSetStatusToTrialAndCycleToMonthly() {
        BillingAccount account = mock(BillingAccount.class);

        BillingSubscription sub = BillingSubscription.createTrial(account);

        assertEquals(SubscriptionStatus.TRIAL, sub.getStatus());
        assertEquals(BillingCycle.MONTHLY, sub.getCycle());
        assertSame(account, sub.getBillingAccount());
    }

    @Test
    void activate_shouldSetStatusToActiveAndUpdateFields() {
        BillingSubscription sub = buildTrialSubscription();
        LocalDate nextDue = LocalDate.now().plusDays(30);

        sub.activate("ext-sub-123", nextDue);

        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals("ext-sub-123", sub.getExternalSubscriptionId());
        assertEquals(nextDue, sub.getNextDueDate());
    }

    @Test
    void markPendingPayment_shouldSetCorrectStatus() {
        BillingSubscription sub = buildTrialSubscription();
        sub.markPendingPayment();
        assertEquals(SubscriptionStatus.PENDING_PAYMENT, sub.getStatus());
    }

    @Test
    void markPastDue_shouldSetCorrectStatus() {
        BillingSubscription sub = buildTrialSubscription();
        sub.markPastDue();
        assertEquals(SubscriptionStatus.PAST_DUE, sub.getStatus());
    }

    @Test
    void block_shouldSetStatusToBlocked() {
        BillingSubscription sub = buildTrialSubscription();
        sub.block();
        assertEquals(SubscriptionStatus.BLOCKED, sub.getStatus());
    }

    @Test
    void cancel_shouldSetStatusToCanceled() {
        BillingSubscription sub = buildTrialSubscription();
        sub.cancel();
        assertEquals(SubscriptionStatus.CANCELED, sub.getStatus());
    }

    @Test
    void calculateTotalCents_shouldSumAllItemValues() {
        BillingSubscription sub = BillingSubscription.builder()
                .status(SubscriptionStatus.TRIAL)
                .cycle(BillingCycle.MONTHLY)
                .billingAccount(mock(BillingAccount.class))
                .build();

        BillingSubscriptionItem item1 = BillingSubscriptionItem.builder()
                .valueCents(1000L).billingSubscription(sub).build();
        BillingSubscriptionItem item2 = BillingSubscriptionItem.builder()
                .valueCents(2500L).billingSubscription(sub).build();

        sub.addItem(item1);
        sub.addItem(item2);

        assertEquals(3500L, sub.calculateTotalCents());
    }

    @Test
    void calculateTotalCents_shouldReturnZeroWhenNoItems() {
        BillingSubscription sub = BillingSubscription.builder()
                .status(SubscriptionStatus.TRIAL)
                .cycle(BillingCycle.MONTHLY)
                .billingAccount(mock(BillingAccount.class))
                .build();

        assertEquals(0L, sub.calculateTotalCents());
    }

    @Test
    void addItem_shouldLinkItemToSubscription() {
        BillingSubscription sub = buildTrialSubscription();
        BillingSubscriptionItem item = BillingSubscriptionItem.builder()
                .valueCents(500L).build();

        sub.addItem(item);

        assertSame(sub, item.getBillingSubscription());
        assertEquals(1, sub.getItems().size());
    }

    // -----------------------------------------------------------------------
    // Lifecycle transitions
    // -----------------------------------------------------------------------

    @Test
    void trialToActiveTransition() {
        BillingSubscription sub = buildTrialSubscription();
        assertEquals(SubscriptionStatus.TRIAL, sub.getStatus());

        sub.activate("ext-id", LocalDate.now().plusDays(30));
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
    }

    @Test
    void activeToPastDueToBlockedTransition() {
        BillingSubscription sub = buildTrialSubscription();
        sub.activate("ext-id", LocalDate.now().plusDays(30));
        sub.markPastDue();
        assertEquals(SubscriptionStatus.PAST_DUE, sub.getStatus());

        sub.block();
        assertEquals(SubscriptionStatus.BLOCKED, sub.getStatus());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BillingSubscription buildTrialSubscription() {
        return BillingSubscription.createTrial(mock(BillingAccount.class));
    }
}
