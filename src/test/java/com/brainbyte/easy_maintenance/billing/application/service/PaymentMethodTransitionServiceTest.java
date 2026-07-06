package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentMethodTransitionServiceTest {

    @Mock BillingAccountRepository billingAccountRepository;
    @Mock BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock InvoiceService invoiceService;
    @Mock AsaasClient asaasClient;
    @Mock AsaasProperties asaasProperties;

    @InjectMocks
    PaymentMethodTransitionService service;

    static final Long USER_ID = 1L;

    // -----------------------------------------------------------------------
    // CC → PIX
    // -----------------------------------------------------------------------

    @Test
    void transitionToPixFromCard_active_cancelsAsaasSubAndSetsPixMethod() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, "ext-sub-001");

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionToPixFromCard(USER_ID);

        verify(asaasClient).cancelSubscription("ext-sub-001");
        assertThat(sub.getExternalSubscriptionId()).isNull();
        assertThat(account.getPaymentMethod()).isEqualTo(PaymentMethodType.PIX);
    }

    @Test
    void transitionToPixFromCard_noExternalSub_skipsCancel() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transitionToPixFromCard(USER_ID);

        verify(asaasClient, never()).cancelSubscription(any());
        assertThat(account.getPaymentMethod()).isEqualTo(PaymentMethodType.PIX);
    }

    @Test
    void transitionToPixFromCard_cancelThrows_continuesSavingMethod() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, "ext-sub-001");

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("404 Asaas")).when(asaasClient).cancelSubscription(any());

        service.transitionToPixFromCard(USER_ID);

        assertThat(account.getPaymentMethod()).isEqualTo(PaymentMethodType.PIX);
    }

    @Test
    void transitionToPixFromCard_notActive_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.PAST_DUE, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.transitionToPixFromCard(USER_ID))
                .isInstanceOf(RuleException.class);

        verify(asaasClient, never()).cancelSubscription(any());
    }

    @Test
    void transitionToPixFromCard_methodAlreadyPix_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.transitionToPixFromCard(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("CC→PIX inválida");
    }

    // -----------------------------------------------------------------------
    // CC → CC (initiateCardUpdate)
    // -----------------------------------------------------------------------

    @Test
    void initiateCardUpdate_active_createsCheckoutAndSavesPayment() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscriptionWithDueDate(SubscriptionStatus.ACTIVE, "ext-sub-001", LocalDate.of(2026, 8, 1));
        Invoice invoice = invoice(4990);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice));
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("https://ok");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("https://cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("https://expired");
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(30);
        AsaasDTO.CheckoutResponse resp = new AsaasDTO.CheckoutResponse("checkout-new-001", "https://asaas.com/checkout/new", null, null);
        when(asaasClient.createCheckout(any())).thenReturn(resp);
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        BillingAccountDTO.CardUpdateResponse result = service.initiateCardUpdate(USER_ID);

        assertThat(result.paymentId()).isEqualTo(99L);
        assertThat(result.checkoutLink()).isEqualTo("https://asaas.com/checkout/new");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getExternalReference()).startsWith(PaymentMethodTransitionService.CARD_UPDATE_PREFIX);
        assertThat(saved.getMethodType()).isEqualTo(PaymentMethodType.CARD);
    }

    @Test
    void initiateCardUpdate_notActive_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.PAST_DUE, "ext-sub-001");

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.initiateCardUpdate(USER_ID))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void initiateCardUpdate_methodIsPix_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.initiateCardUpdate(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Cartão de Crédito");
    }

    @Test
    void initiateCardUpdate_invoiceEmpty_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscriptionWithDueDate(SubscriptionStatus.ACTIVE, "ext-sub-001", LocalDate.of(2026, 8, 1));

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.initiateCardUpdate(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("fatura");
    }

    // -----------------------------------------------------------------------
    // PIX → CC
    // -----------------------------------------------------------------------

    @Test
    void transitionToCardFromPix_noPendingPix_returnsSuccessMessage() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of());
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillingAccountDTO.PaymentMethodTransitionResponse resp = service.transitionToCardFromPix(USER_ID);

        assertThat(resp.warning()).isNull();
        assertThat(resp.effectiveCycle()).isNull();
        assertThat(account.getPaymentMethod()).isEqualTo(PaymentMethodType.CARD);
    }

    @Test
    void transitionToCardFromPix_withPendingPix_returnsWarningAndEffectiveCycle() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);
        Payment pendingPix = Payment.builder().id(55L).cycleNumber(3).status(PaymentStatus.PENDING).build();

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of(pendingPix));
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillingAccountDTO.PaymentMethodTransitionResponse resp = service.transitionToCardFromPix(USER_ID);

        assertThat(resp.warning()).isNotNull().contains("ciclo");
        assertThat(resp.effectiveCycle()).isEqualTo(4);
        assertThat(account.getPaymentMethod()).isEqualTo(PaymentMethodType.CARD);
    }

    @Test
    void transitionToCardFromPix_pendingPixNullCycle_defaultsToEffectiveCycle2() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);
        Payment pendingPix = Payment.builder().id(55L).cycleNumber(null).status(PaymentStatus.PENDING).build();

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of(pendingPix));
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BillingAccountDTO.PaymentMethodTransitionResponse resp = service.transitionToCardFromPix(USER_ID);

        assertThat(resp.effectiveCycle()).isEqualTo(2);
    }

    @Test
    void transitionToCardFromPix_notActive_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.TRIAL, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.transitionToCardFromPix(USER_ID))
                .isInstanceOf(RuleException.class);
    }

    @Test
    void transitionToCardFromPix_methodAlreadyCard_throwsRuleException() {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.ACTIVE, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.transitionToCardFromPix(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("PIX→CC inválida");
    }

    // -----------------------------------------------------------------------
    // Not-found scenarios
    // -----------------------------------------------------------------------

    @Test
    void transitionToPix_accountNotFound_throwsNotFoundException() {
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transitionToPixFromCard(USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void transitionToCard_subscriptionNotFound_throwsNotFoundException() {
        BillingAccount account = account(PaymentMethodType.PIX, "cus_abc");
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transitionToCardFromPix(USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(names = {"TRIAL", "PAST_DUE", "CANCELED", "PENDING_ACTIVATION"})
    void initiateCardUpdate_nonActiveStatuses_throwsRuleException(SubscriptionStatus status) {
        BillingAccount account = account(PaymentMethodType.CARD, "cus_abc");
        BillingSubscription sub = subscription(status, null);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> service.initiateCardUpdate(USER_ID))
                .isInstanceOf(RuleException.class);
    }

    // -----------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------

    private BillingAccount account(PaymentMethodType method, String externalCustomerId) {
        BillingAccount a = BillingAccount.builder()
                .id(10L)
                .user(User.builder().id(USER_ID).build())
                .paymentMethod(method)
                .externalCustomerId(externalCustomerId)
                .build();
        a.setPaymentMethod(method);
        return a;
    }

    private BillingSubscription subscription(SubscriptionStatus status, String externalSubId) {
        return BillingSubscription.builder()
                .id(20L)
                .status(status)
                .externalSubscriptionId(externalSubId)
                .nextDueDate(LocalDate.now().plusMonths(1))
                .build();
    }

    private BillingSubscription subscriptionWithDueDate(SubscriptionStatus status, String externalSubId, LocalDate dueDate) {
        return BillingSubscription.builder()
                .id(20L)
                .status(status)
                .externalSubscriptionId(externalSubId)
                .nextDueDate(dueDate)
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
