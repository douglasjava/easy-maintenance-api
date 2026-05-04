package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository repository;
    @Mock private BillingAccountRepository billingAccountRepository;
    @Mock private BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock private BillingSubscriptionItemRepository itemRepository;
    @Mock private PaymentRepository paymentRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void generateInvoices_shouldCreateInvoicePerEligibleSubscription() {
        var user = User.builder().id(1L).email("user@org.com").build();
        var billingAccount = buildBillingAccount(user);
        var subscription = buildSubscription(billingAccount);
        var subscriptionItem = buildItem(subscription);

        var periodStart = LocalDate.of(2026, 5, 1);
        var periodEnd = LocalDate.of(2026, 5, 31);

        when(billingSubscriptionRepository.findEligibleForInvoicing(any(), any()))
                .thenReturn(List.of(subscription));
        when(repository.findByPayerIdAndPeriodStartAndPeriodEnd(1L, periodStart, periodEnd))
                .thenReturn(Optional.empty());
        when(itemRepository.findAllByBillingSubscriptionIdFetchPlan(subscription.getId()))
                .thenReturn(List.of(subscriptionItem));
        when(repository.save(any())).thenAnswer(inv -> {
            Invoice inv2 = inv.getArgument(0);
            inv2.setId(100L);
            return inv2;
        });

        var created = invoiceService.generateInvoices(periodStart, periodEnd,
                List.of(SubscriptionStatus.ACTIVE), null);

        assertThat(created).hasSize(1);
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(repository).save(captor.capture());
        Invoice saved = captor.getValue();

        assertThat(saved.getPayer()).isEqualTo(user);
        assertThat(saved.getPeriodStart()).isEqualTo(periodStart);
        assertThat(saved.getPeriodEnd()).isEqualTo(periodEnd);
        assertThat(saved.getDueDate()).isEqualTo(periodEnd.plusDays(5));
        assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(saved.getTotalCents()).isEqualTo(9900);
    }

    @Test
    void generateInvoices_shouldSkipWhenInvoiceAlreadyExists() {
        var user = User.builder().id(2L).email("other@org.com").build();
        var billingAccount = buildBillingAccount(user);
        var subscription = buildSubscription(billingAccount);

        var periodStart = LocalDate.of(2026, 5, 1);
        var periodEnd = LocalDate.of(2026, 5, 31);

        when(billingSubscriptionRepository.findEligibleForInvoicing(any(), any()))
                .thenReturn(List.of(subscription));
        when(repository.findByPayerIdAndPeriodStartAndPeriodEnd(2L, periodStart, periodEnd))
                .thenReturn(Optional.of(Invoice.builder().id(99L).build()));

        var created = invoiceService.generateInvoices(periodStart, periodEnd,
                List.of(SubscriptionStatus.ACTIVE), null);

        assertThat(created).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void generateInvoiceForPayer_shouldThrowNotFound_whenSubscriptionMissing() {
        when(billingSubscriptionRepository.findByBillingAccountUserId(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                invoiceService.generateInvoiceForPayer(99L,
                        LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void generateInvoiceForPayer_happyPath_shouldCreateInvoice() {
        var user = User.builder().id(3L).email("payer@org.com").build();
        var billingAccount = buildBillingAccount(user);
        var subscription = buildSubscription(billingAccount);
        var item = buildItem(subscription);

        var periodStart = LocalDate.of(2026, 5, 1);
        var periodEnd = LocalDate.of(2026, 5, 31);

        when(billingSubscriptionRepository.findByBillingAccountUserId(3L))
                .thenReturn(Optional.of(subscription));
        when(repository.findByPayerIdAndPeriodStartAndPeriodEnd(3L, periodStart, periodEnd))
                .thenReturn(Optional.empty());
        when(itemRepository.findAllByBillingSubscriptionIdFetchPlan(subscription.getId()))
                .thenReturn(List.of(item));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = invoiceService.generateInvoiceForPayer(3L, periodStart, periodEnd);

        assertThat(result).isPresent();
        assertThat(result.get().getTotalCents()).isEqualTo(9900);
    }

    private BillingAccount buildBillingAccount(User user) {
        return BillingAccount.builder()
                .id(1L)
                .user(user)
                .name("Org Test")
                .billingEmail("billing@org.com")
                .externalCustomerId("asaas-cust-001")
                .build();
    }

    private BillingSubscription buildSubscription(BillingAccount account) {
        return BillingSubscription.builder()
                .id(10L)
                .billingAccount(account)
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    private BillingSubscriptionItem buildItem(BillingSubscription subscription) {
        var plan = BillingPlan.builder().code("BASIC").name("Básico").priceCents(9900).build();
        return BillingSubscriptionItem.builder()
                .id(1L)
                .billingSubscription(subscription)
                .plan(plan)
                .valueCents(9900L)
                .sourceType(BillingSubscriptionItemSourceType.USER)
                .sourceId("user-001")
                .build();
    }
}
