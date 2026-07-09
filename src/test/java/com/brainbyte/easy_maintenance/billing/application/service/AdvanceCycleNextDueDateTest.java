package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdvanceCycleNextDueDateTest {

    @Mock private BillingSubscriptionRepository repository;
    @Mock private BillingSubscriptionItemRepository itemRepository;
    @Mock private AsaasClient asaasClient;
    @Mock private BillingNotificationService billingNotificationService;

    @InjectMocks
    private BillingSubscriptionService service;

    private Payment pixPayment;

    @BeforeEach
    void setUp() {
        pixPayment = Payment.builder()
                .id(1L)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .cycleNumber(1)
                .amountCents(9900)
                .currency("BRL")
                .externalReference("BILLING-1-CYCLE-1")
                .externalPaymentId("pay-001")
                .build();
    }

    @Test
    void advanceCycle_monthly_setsNextDueDateOneMonthAhead() {
        Instant periodEnd = Instant.now();
        BillingSubscription sub = buildPixSubscription(BillingCycle.MONTHLY, periodEnd);

        service.advanceCycle(sub, pixPayment);

        assertThat(sub.getNextDueDate()).isNotNull();

        LocalDate expectedNextDue = periodEnd.atZone(ZoneOffset.UTC).toLocalDate().plusMonths(1);
        assertThat(sub.getNextDueDate()).isEqualTo(expectedNextDue);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(repository).save(sub);
    }

    @Test
    void advanceCycle_yearly_setsNextDueDateOneYearAhead() {
        Instant periodEnd = Instant.now();
        BillingSubscription sub = buildPixSubscription(BillingCycle.YEARLY, periodEnd);

        service.advanceCycle(sub, pixPayment);

        assertThat(sub.getNextDueDate()).isNotNull();

        LocalDate expectedNextDue = periodEnd.atZone(ZoneOffset.UTC).toLocalDate().plusYears(1);
        assertThat(sub.getNextDueDate()).isEqualTo(expectedNextDue);
        verify(repository).save(sub);
    }

    @Test
    void advanceCycle_nullCurrentPeriodEnd_usesNowAsBaseForNextDueDate() {
        BillingSubscription sub = buildPixSubscription(BillingCycle.MONTHLY, null);

        service.advanceCycle(sub, pixPayment);

        assertThat(sub.getNextDueDate()).isNotNull();
        assertThat(sub.getNextDueDate()).isAfter(LocalDate.now().minusDays(1));
        verify(repository).save(sub);
    }

    @Test
    void advanceCycle_canceledSubscription_earlyReturnDoesNotSetNextDueDate() {
        BillingSubscription sub = buildPixSubscription(BillingCycle.MONTHLY, Instant.now());
        sub.setStatus(SubscriptionStatus.CANCELED);
        LocalDate originalNextDue = sub.getNextDueDate();

        service.advanceCycle(sub, pixPayment);

        assertThat(sub.getNextDueDate()).isEqualTo(originalNextDue);
        verify(repository, never()).save(any());
    }

    private BillingSubscription buildPixSubscription(BillingCycle cycle, Instant currentPeriodEnd) {
        return BillingSubscription.builder()
                .id(1L)
                .status(SubscriptionStatus.PAST_DUE)
                .cycle(cycle)
                .currentPeriodEnd(currentPeriodEnd)
                .externalSubscriptionId(null)
                .build();
    }
}
