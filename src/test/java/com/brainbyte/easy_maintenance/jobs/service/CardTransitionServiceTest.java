package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.PaymentMethodTransitionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardTransitionServiceTest {

    @Mock BillingSubscriptionRepository subscriptionRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock InvoiceService invoiceService;
    @Mock AsaasClient asaasClient;
    @Mock AsaasProperties asaasProperties;
    @Mock PaymentMethodTransitionService transitionService;

    @InjectMocks
    CardTransitionService service;

    // -----------------------------------------------------------------------
    // processCardTransitions
    // -----------------------------------------------------------------------

    @Test
    void processCardTransitions_eligibleSubscription_createsCheckout() {
        BillingSubscription sub = buildSubscription();

        when(subscriptionRepository.findPendingCardTransitions(any())).thenReturn(List.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of());
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any()))
                .thenReturn(Optional.of(invoice(2990)));
        AsaasDTO.CreateCheckoutRequest mockReq = mock(AsaasDTO.CreateCheckoutRequest.class);
        when(transitionService.buildCheckoutRequest(any(), any(), any(), anyLong(), any())).thenReturn(mockReq);
        when(asaasClient.createCheckout(any())).thenReturn(new AsaasDTO.CheckoutResponse("checkout-001", "https://link", null, null));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processCardTransitions(5);

        verify(paymentRepository).save(any());
    }

    @Test
    void processCardTransitions_hasPendingPayment_skipsCheckoutCreation() {
        BillingSubscription sub = buildSubscription();
        Payment pending = Payment.builder().id(1L).build();

        when(subscriptionRepository.findPendingCardTransitions(any())).thenReturn(List.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of(pending));

        service.processCardTransitions(5);

        verify(paymentRepository, never()).save(any());
        verify(asaasClient, never()).createCheckout(any());
    }

    @Test
    void processCardTransitions_noEligibleSubscriptions_doesNothing() {
        when(subscriptionRepository.findPendingCardTransitions(any())).thenReturn(List.of());

        service.processCardTransitions(5);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processCardTransitions_invoiceEmpty_skipsCheckoutCreation() {
        BillingSubscription sub = buildSubscription();

        when(subscriptionRepository.findPendingCardTransitions(any())).thenReturn(List.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of());
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.empty());

        service.processCardTransitions(5);

        verify(paymentRepository, never()).save(any());
        verify(asaasClient, never()).createCheckout(any());
    }

    @Test
    void processCardTransitions_asaasThrows_logsErrorAndContinues() {
        BillingSubscription sub1 = buildSubscription();
        BillingSubscription sub2 = buildSubscription();
        sub2.setId(99L);

        when(subscriptionRepository.findPendingCardTransitions(any())).thenReturn(List.of(sub1, sub2));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of());
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice(2990)));
        AsaasDTO.CreateCheckoutRequest mockReq = mock(AsaasDTO.CreateCheckoutRequest.class);
        when(transitionService.buildCheckoutRequest(any(), any(), any(), anyLong(), any())).thenReturn(mockReq);
        when(asaasClient.createCheckout(any())).thenThrow(new RuntimeException("Asaas down"));

        service.processCardTransitions(5);

        verify(asaasClient, times(2)).createCheckout(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processTransition_savesPaymentWithCardUpdatePrefix() {
        BillingSubscription sub = buildSubscription();

        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of());
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice(4990)));
        AsaasDTO.CreateCheckoutRequest mockReq = mock(AsaasDTO.CreateCheckoutRequest.class);
        when(transitionService.buildCheckoutRequest(any(), any(), any(), anyLong(), any())).thenReturn(mockReq);
        when(asaasClient.createCheckout(any())).thenReturn(new AsaasDTO.CheckoutResponse("checkout-pix-cc", "https://link", null, null));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processTransition(sub);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getExternalReference()).startsWith(PaymentMethodTransitionService.CARD_UPDATE_PREFIX);
        assertThat(saved.getMethodType()).isEqualTo(PaymentMethodType.CARD);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    // -----------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------

    private BillingSubscription buildSubscription() {
        User user = User.builder().id(1L).build();
        BillingAccount account = BillingAccount.builder()
                .id(10L)
                .user(user)
                .paymentMethod(PaymentMethodType.CARD)
                .externalCustomerId("cus_abc")
                .build();
        return BillingSubscription.builder()
                .id(20L)
                .status(SubscriptionStatus.ACTIVE)
                .billingAccount(account)
                .currentPeriodEnd(Instant.now().plus(3, ChronoUnit.DAYS))
                .build();
    }

    private Invoice invoice(int cents) {
        BillingPlan plan = BillingPlan.builder()
                .id(1L)
                .name("Pro")
                .billingCycle(BillingCycle.MONTHLY)
                .build();
        InvoiceItem item = InvoiceItem.builder()
                .id(1L)
                .amountCents(cents)
                .quantity(1)
                .description("Plano Pro")
                .plan(plan)
                .build();
        return Invoice.builder()
                .id(1L)
                .totalCents(cents)
                .currency("BRL")
                .items(List.of(item))
                .build();
    }
}
