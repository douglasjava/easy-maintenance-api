package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.CriticalEmailDispatchService;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.application.factory.PaymentProviderFactory;
import com.brainbyte.easy_maintenance.payment.application.service.PaymentProviderStrategy;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrialExpirationServiceTest {

    @Mock private InvoiceService invoiceService;
    @Mock private BillingAccountRepository billingAccountRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private AsaasClient asaasClient;
    @Mock private AsaasProperties asaasProperties;
    @Mock private CriticalEmailDispatchService criticalEmailDispatchService;
    @Mock private EmailTemplateHelper emailTemplateHelper;
    @Mock private BillingSubscriptionService billingSubscriptionService;
    @Mock private PaymentProviderFactory paymentProviderFactory;
    @Mock private PaymentProviderStrategy paymentProviderStrategy;

    @InjectMocks
    private TrialExpirationService service;

    private User payer;
    private BillingPlan plan;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        payer = User.builder()
                .id(1L)
                .email("payer@test.com")
                .name("Payer Test")
                .build();

        plan = BillingPlan.builder()
                .code("BUSINESS")
                .name("Business")
                .billingCycle(BillingCycle.MONTHLY)
                .priceCents(9900)
                .build();

        InvoiceItem item = InvoiceItem.builder()
                .id(1L)
                .plan(plan)
                .description("Business Plan - Monthly")
                .quantity(1)
                .amountCents(9900)
                .build();

        invoice = Invoice.builder()
                .id(1L)
                .payer(payer)
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now().plusMonths(1).minusDays(1))
                .dueDate(LocalDate.now().plusDays(7))
                .totalCents(9900)
                .items(List.of(item))
                .build();
    }

    @Test
    void whenExternalCustomerIdAbsent_andAsaasCreateFails_thenIllegalStateExceptionIsThrown() {
        BillingAccount account = BillingAccount.builder()
                .id(1L)
                .user(payer)
                .billingEmail("billing@test.com")
                .name("Test Company")
                .paymentMethod(PaymentMethodType.PIX)
                // externalCustomerId is null — simulates onboarding failure
                .build();

        when(invoiceService.generateInvoices(any(), any(), any(), any()))
                .thenReturn(List.of(invoice));
        when(billingAccountRepository.findByUserId(payer.getId()))
                .thenReturn(Optional.of(account));
        when(paymentProviderFactory.get(PaymentProvider.ASAAS))
                .thenReturn(paymentProviderStrategy);
        when(paymentProviderStrategy.createExternalCustomer(any()))
                .thenThrow(new RuntimeException("Asaas unavailable"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.processTrialsExpiringWithinDays(1)
        );

        assertTrue(ex.getMessage().contains("payerId=1"));
        verify(billingAccountRepository, never()).save(account);
        verify(criticalEmailDispatchService, never()).send(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void whenExternalCustomerIdAbsent_andAsaasCreateSucceeds_thenAccountSavedAndEmailSent() {
        BillingAccount account = BillingAccount.builder()
                .id(1L)
                .user(payer)
                .billingEmail("billing@test.com")
                .name("Test Company")
                .paymentMethod(PaymentMethodType.PIX)
                // externalCustomerId is null — simulates onboarding failure
                .build();

        BillingSubscription subscription = mock(BillingSubscription.class);
        when(subscription.getId()).thenReturn(10L);

        AsaasDTO.CheckoutResponse checkoutResponse =
                new AsaasDTO.CheckoutResponse("checkout-id-123", "http://pay.link", null, null);

        Payment savedPayment = Payment.builder()
                .id(99L)
                .paymentLink("http://pay.link")
                .payer(payer)
                .build();

        when(invoiceService.generateInvoices(any(), any(), any(), any()))
                .thenReturn(List.of(invoice));
        when(billingAccountRepository.findByUserId(payer.getId()))
                .thenReturn(Optional.of(account));
        when(paymentProviderFactory.get(PaymentProvider.ASAAS))
                .thenReturn(paymentProviderStrategy);
        when(paymentProviderStrategy.createExternalCustomer(any()))
                .thenReturn("cust_abc123");
        when(billingAccountRepository.save(account))
                .thenReturn(account);
        when(billingSubscriptionService.findByUser(payer.getId()))
                .thenReturn(Optional.of(subscription));
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(60);
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("http://success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("http://cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("http://expired");
        when(asaasClient.createCheckout(any())).thenReturn(checkoutResponse);
        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(emailTemplateHelper.generateSubscriptionExpirationHtml(any(), any(), any()))
                .thenReturn("<html>expiring</html>");

        assertDoesNotThrow(() -> service.processTrialsExpiringWithinDays(1));

        assertEquals("cust_abc123", account.getExternalCustomerId());
        verify(billingAccountRepository).save(account);
        verify(criticalEmailDispatchService).send(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void whenExternalCustomerIdPresent_thenJustInTimeCreationNotAttempted() {
        BillingAccount account = BillingAccount.builder()
                .id(1L)
                .user(payer)
                .billingEmail("billing@test.com")
                .name("Test Company")
                .paymentMethod(PaymentMethodType.PIX)
                .externalCustomerId("cust_already_set")
                .build();

        BillingSubscription subscription = mock(BillingSubscription.class);
        when(subscription.getId()).thenReturn(10L);

        AsaasDTO.CheckoutResponse checkoutResponse =
                new AsaasDTO.CheckoutResponse("checkout-id-456", "http://pay.link", null, null);

        Payment savedPayment = Payment.builder()
                .id(100L)
                .paymentLink("http://pay.link")
                .payer(payer)
                .build();

        when(invoiceService.generateInvoices(any(), any(), any(), any()))
                .thenReturn(List.of(invoice));
        when(billingAccountRepository.findByUserId(payer.getId()))
                .thenReturn(Optional.of(account));
        when(billingSubscriptionService.findByUser(payer.getId()))
                .thenReturn(Optional.of(subscription));
        when(asaasProperties.checkoutMinutesToExpire()).thenReturn(60);
        when(asaasProperties.checkoutSuccessUrl()).thenReturn("http://success");
        when(asaasProperties.checkoutCancelUrl()).thenReturn("http://cancel");
        when(asaasProperties.checkoutExpiredUrl()).thenReturn("http://expired");
        when(asaasClient.createCheckout(any())).thenReturn(checkoutResponse);
        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(emailTemplateHelper.generateSubscriptionExpirationHtml(any(), any(), any()))
                .thenReturn("<html>expiring</html>");

        assertDoesNotThrow(() -> service.processTrialsExpiringWithinDays(1));

        verify(paymentProviderFactory, never()).get(any());
        verify(criticalEmailDispatchService).send(any(), any(), any(), any(), any(), any(), anyBoolean());
    }
}
