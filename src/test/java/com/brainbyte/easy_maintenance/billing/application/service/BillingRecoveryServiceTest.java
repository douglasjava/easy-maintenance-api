package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingRecoveryServiceTest {

    @Mock BillingAccountRepository billingAccountRepository;
    @Mock BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock InvoiceService invoiceService;
    @Mock AsaasClient asaasClient;
    @Mock AsaasProperties asaasProperties;

    @InjectMocks
    BillingRecoveryService service;

    static final Long USER_ID = 1L;

    // -----------------------------------------------------------------------
    // recoverWithPix — happy path
    // -----------------------------------------------------------------------

    @Test
    void recoverWithPix_pastDue_createsPixPaymentAndReturnsQrCode() {
        BillingAccount account = account("cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.PAST_DUE);
        Invoice invoice = invoice(1990);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING))).thenReturn(List.of());
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(anyLong())).thenReturn(2);

        AsaasDTO.PaymentResponse asaasResp = new AsaasDTO.PaymentResponse(
                "pay_123", "cus_abc", AsaasDTO.BillingType.PIX,
                new BigDecimal("19.90"), LocalDate.now(), "PENDING",
                "https://asaas.com/invoice/pay_123", null
        );
        when(asaasClient.createPayment(any())).thenReturn(asaasResp);

        AsaasDTO.PixQrCode qr = new AsaasDTO.PixQrCode("base64img==", "00020126...", null);
        when(asaasClient.getPixQrCode("pay_123")).thenReturn(qr);

        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        BillingAccountDTO.RecoveryPixResponse resp = service.recoverWithPix(USER_ID);

        assertThat(resp.paymentId()).isEqualTo(99L);
        assertThat(resp.paymentLink()).isEqualTo("https://asaas.com/invoice/pay_123");
        assertThat(resp.pixQrCodeBase64()).isEqualTo("base64img==");
        assertThat(resp.pixQrCode()).isEqualTo("00020126...");
        assertThat(resp.pixExpiresAt()).isNull();

        ArgumentCaptor<Payment> savedPayment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(savedPayment.capture());
        assertThat(savedPayment.getValue().getCycleNumber()).isEqualTo(3);
        assertThat(savedPayment.getValue().getExternalPaymentId()).isEqualTo("pay_123");
    }

    @Test
    void recoverWithPix_qrCodeFetchFails_stillReturnsPaymentLinkWithoutQr() {
        BillingAccount account = account("cus_abc");
        Invoice invoice = invoice(1990);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.PAST_DUE)));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice));
        when(paymentRepository.findMaxCycleNumberByBillingSubscriptionId(anyLong())).thenReturn(0);

        AsaasDTO.PaymentResponse asaasResp = new AsaasDTO.PaymentResponse(
                "pay_456", "cus_abc", AsaasDTO.BillingType.PIX,
                BigDecimal.valueOf(19.90), LocalDate.now(), "PENDING",
                "https://link.pix", null
        );
        when(asaasClient.createPayment(any())).thenReturn(asaasResp);
        when(asaasClient.getPixQrCode(any())).thenThrow(new RuntimeException("QR code service timeout"));

        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(88L);
            return p;
        });

        BillingAccountDTO.RecoveryPixResponse resp = service.recoverWithPix(USER_ID);

        assertThat(resp.paymentId()).isEqualTo(88L);
        assertThat(resp.paymentLink()).isEqualTo("https://link.pix");
        assertThat(resp.pixQrCode()).isNull();
        assertThat(resp.pixQrCodeBase64()).isNull();
    }

    // -----------------------------------------------------------------------
    // recoverWithPix — error scenarios
    // -----------------------------------------------------------------------

    @Test
    void recoverWithPix_pendingPaymentExists_throwsConflict() {
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account("cus_x")));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription(SubscriptionStatus.PAST_DUE)));
        when(paymentRepository.findByBillingSubscriptionIdAndStatus(anyLong(), eq(PaymentStatus.PENDING)))
                .thenReturn(List.of(mock(Payment.class)));

        assertThatThrownBy(() -> service.recoverWithPix(USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pagamento pendente");

        verify(asaasClient, never()).createPayment(any());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"TRIAL", "ACTIVE", "CANCELED", "BLOCKED", "PENDING_PAYMENT"})
    void recoverWithPix_notPastDue_throwsRuleException(SubscriptionStatus status) {
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account("cus_x")));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription(status)));

        assertThatThrownBy(() -> service.recoverWithPix(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("PAST_DUE");

        verify(asaasClient, never()).createPayment(any());
    }

    @Test
    void recoverWithPix_accountNotFound_throwsNotFound() {
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recoverWithPix(USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void recoverWithPix_subscriptionNotFound_throwsNotFound() {
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account("cus_x")));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recoverWithPix(USER_ID))
                .isInstanceOf(NotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // recoverWithCheckout — happy path
    // -----------------------------------------------------------------------

    @Test
    void recoverWithCheckout_pastDue_withExistingAsaasSubscription_cancelsThenCreatesCheckout() {
        BillingAccount account = account("cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.PAST_DUE);
        sub.setExternalSubscriptionId("sub_old");
        Invoice invoice = invoice(1990);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice));
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("https://app/success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("https://app/cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("https://app/expired");
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(30);

        AsaasDTO.CheckoutResponse checkoutResp = new AsaasDTO.CheckoutResponse(
                "chk_789", "https://pay.asaas.com/chk_789", null, null
        );
        when(asaasClient.createCheckout(any())).thenReturn(checkoutResp);
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(77L);
            return p;
        });

        BillingAccountDTO.RecoveryCheckoutResponse resp = service.recoverWithCheckout(USER_ID);

        assertThat(resp.paymentId()).isEqualTo(77L);
        assertThat(resp.checkoutLink()).isEqualTo("https://pay.asaas.com/chk_789");

        verify(asaasClient).cancelSubscription("sub_old");
        assertThat(sub.getExternalSubscriptionId()).isNull();

        ArgumentCaptor<Payment> savedPayment = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(savedPayment.capture());
        assertThat(savedPayment.getValue().getExternalPaymentId()).isEqualTo("chk_789");
    }

    @Test
    void recoverWithCheckout_pastDue_withoutAsaasSubscription_skipsCancelAndCreatesCheckout() {
        BillingAccount account = account("cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.PAST_DUE);
        // externalSubscriptionId is null — PIX user, never had Asaas sub
        Invoice invoice = invoice(1990);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice));
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("https://app/success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("https://app/cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("https://app/expired");
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(30);

        when(asaasClient.createCheckout(any())).thenReturn(
                new AsaasDTO.CheckoutResponse("chk_000", "https://pay.asaas.com/chk_000", null, null));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(66L);
            return p;
        });

        BillingAccountDTO.RecoveryCheckoutResponse resp = service.recoverWithCheckout(USER_ID);

        assertThat(resp.checkoutLink()).isEqualTo("https://pay.asaas.com/chk_000");
        verify(asaasClient, never()).cancelSubscription(any());
    }

    @Test
    void recoverWithCheckout_cancelAsaasFails_stillProceedsWithCheckout() {
        BillingAccount account = account("cus_abc");
        BillingSubscription sub = subscription(SubscriptionStatus.PAST_DUE);
        sub.setExternalSubscriptionId("sub_gone");
        Invoice invoice = invoice(1990);

        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.of(sub));
        when(invoiceService.generateInvoiceForPayer(anyLong(), any(), any())).thenReturn(Optional.of(invoice));
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("https://app/success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("https://app/cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("https://app/expired");
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(30);

        doThrow(new RuntimeException("Asaas 404 - subscription not found"))
                .when(asaasClient).cancelSubscription("sub_gone");

        when(asaasClient.createCheckout(any())).thenReturn(
                new AsaasDTO.CheckoutResponse("chk_111", "https://pay.asaas.com/chk_111", null, null));
        when(billingSubscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(55L);
            return p;
        });

        BillingAccountDTO.RecoveryCheckoutResponse resp = service.recoverWithCheckout(USER_ID);

        assertThat(resp.paymentId()).isEqualTo(55L);
        assertThat(sub.getExternalSubscriptionId()).isNull();
    }

    // -----------------------------------------------------------------------
    // recoverWithCheckout — error scenarios
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"TRIAL", "ACTIVE", "CANCELED", "BLOCKED", "PENDING_PAYMENT"})
    void recoverWithCheckout_notPastDue_throwsRuleException(SubscriptionStatus status) {
        when(billingAccountRepository.findByUserId(USER_ID)).thenReturn(Optional.of(account("cus_x")));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription(status)));

        assertThatThrownBy(() -> service.recoverWithCheckout(USER_ID))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("PAST_DUE");

        verify(asaasClient, never()).createCheckout(any());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BillingAccount account(String externalCustomerId) {
        User user = new User();
        user.setId(USER_ID);
        return BillingAccount.builder()
                .id(10L)
                .user(user)
                .externalCustomerId(externalCustomerId)
                .build();
    }

    private BillingSubscription subscription(SubscriptionStatus status) {
        return BillingSubscription.builder()
                .id(20L)
                .status(status)
                .cycle(BillingCycle.MONTHLY)
                .build();
    }

    private Invoice invoice(int totalCents) {
        User user = new User();
        user.setId(USER_ID);
        return Invoice.builder()
                .id(5L)
                .payer(user)
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now().plusMonths(1))
                .totalCents(totalCents)
                .currency("BRL")
                .items(List.of())
                .build();
    }
}
