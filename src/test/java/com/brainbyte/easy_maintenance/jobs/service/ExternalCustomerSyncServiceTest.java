package com.brainbyte.easy_maintenance.jobs.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.application.factory.PaymentProviderFactory;
import com.brainbyte.easy_maintenance.payment.application.service.PaymentProviderStrategy;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ExternalCustomerSyncServiceTest {

    @Mock private BillingAccountRepository billingAccountRepository;
    @Mock private PaymentProviderFactory paymentProviderFactory;
    @Mock private PaymentProviderStrategy paymentProviderStrategy;

    @InjectMocks
    private ExternalCustomerSyncService service;

    @BeforeEach
    void setUp() {
        lenient().when(paymentProviderFactory.get(PaymentProvider.ASAAS)).thenReturn(paymentProviderStrategy);
    }

    @Test
    void whenNoAccountsMissingExternalCustomerId_thenNothingIsProcessed() {
        when(billingAccountRepository.findByExternalCustomerIdIsNull()).thenReturn(List.of());

        ExternalCustomerSyncResult result = service.syncMissingExternalCustomerIds();

        assertEquals(new ExternalCustomerSyncResult(0, 0, 0), result);
        verify(paymentProviderFactory, never()).get(any());
        verify(billingAccountRepository, never()).save(any());
    }

    @Test
    void whenAllAccountsSucceed_thenAllAreSavedWithExternalCustomerId() {
        User user1 = User.builder().id(1L).email("a@test.com").name("A").build();
        User user2 = User.builder().id(2L).email("b@test.com").name("B").build();

        BillingAccount account1 = BillingAccount.builder()
                .id(1L).user(user1).billingEmail("a@test.com")
                .paymentMethod(PaymentMethodType.PIX).build();

        BillingAccount account2 = BillingAccount.builder()
                .id(2L).user(user2).billingEmail("b@test.com")
                .paymentMethod(PaymentMethodType.PIX).build();

        when(billingAccountRepository.findByExternalCustomerIdIsNull()).thenReturn(List.of(account1, account2));
        when(paymentProviderStrategy.createExternalCustomer(any()))
                .thenReturn("cust_aaa")
                .thenReturn("cust_bbb");
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExternalCustomerSyncResult result = service.syncMissingExternalCustomerIds();

        assertEquals(new ExternalCustomerSyncResult(2, 2, 0), result);
        verify(paymentProviderStrategy, times(2)).createExternalCustomer(any());
        verify(billingAccountRepository).save(argThat(a -> "cust_aaa".equals(a.getExternalCustomerId())));
        verify(billingAccountRepository).save(argThat(a -> "cust_bbb".equals(a.getExternalCustomerId())));
    }

    @Test
    void whenOneAccountFailsAndAnotherSucceeds_thenBothAttemptedAndMethodCompletes() {
        User user1 = User.builder().id(1L).email("ok@test.com").name("OK").build();
        User user2 = User.builder().id(2L).email("fail@test.com").name("Fail").build();

        BillingAccount successAccount = BillingAccount.builder()
                .id(1L).user(user1).billingEmail("ok@test.com")
                .paymentMethod(PaymentMethodType.PIX).build();

        BillingAccount failAccount = BillingAccount.builder()
                .id(2L).user(user2).billingEmail("fail@test.com")
                .paymentMethod(PaymentMethodType.PIX).build();

        when(billingAccountRepository.findByExternalCustomerIdIsNull())
                .thenReturn(List.of(successAccount, failAccount));
        when(paymentProviderStrategy.createExternalCustomer(any()))
                .thenReturn("cust_ok")
                .thenThrow(new RuntimeException("Asaas timeout"));
        when(billingAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // deve completar sem lançar exceção — falha individual não para o batch
        ExternalCustomerSyncResult result = service.syncMissingExternalCustomerIds();

        assertEquals(new ExternalCustomerSyncResult(2, 1, 1), result);
        // conta bem-sucedida foi salva com o ID correto
        verify(billingAccountRepository).save(argThat(a -> "cust_ok".equals(a.getExternalCustomerId())));
        // conta com falha não foi salva (exception antes do save)
        verify(billingAccountRepository, never()).save(argThat(a -> a.getId().equals(2L)));
        // ambas foram tentadas
        verify(paymentProviderStrategy, times(2)).createExternalCustomer(any());
    }

    @Test
    void whenAllAccountsFail_thenMethodCompletesWithoutThrowing() {
        User user1 = User.builder().id(1L).email("fail1@test.com").name("Fail1").build();

        BillingAccount account = BillingAccount.builder()
                .id(1L).user(user1).billingEmail("fail1@test.com")
                .paymentMethod(PaymentMethodType.PIX).build();

        when(billingAccountRepository.findByExternalCustomerIdIsNull()).thenReturn(List.of(account));
        when(paymentProviderStrategy.createExternalCustomer(any()))
                .thenThrow(new RuntimeException("Asaas down"));

        // não deve lançar — o log.error interno já garante observabilidade via Sentry
        ExternalCustomerSyncResult result = service.syncMissingExternalCustomerIds();

        assertEquals(new ExternalCustomerSyncResult(1, 0, 1), result);
        verify(billingAccountRepository, never()).save(any());
    }
}
