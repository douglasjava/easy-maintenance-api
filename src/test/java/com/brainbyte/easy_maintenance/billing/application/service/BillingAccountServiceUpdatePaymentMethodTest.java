package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.error.RefusalReasonClassifier;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingAccountServiceUpdatePaymentMethodTest {

    @Mock BillingAccountRepository repository;
    @Mock UserRepository userRepository;
    @Mock BillingSubscriptionRepository billingSubscriptionRepository;
    @Mock BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock RefusalReasonClassifier refusalReasonClassifier;

    @InjectMocks
    BillingAccountService service;

    private static final Long USER_ID = 1L;

    private BillingAccount account() {
        return BillingAccount.builder().id(10L).paymentMethod(PaymentMethodType.CARD).build();
    }

    private BillingSubscription subscriptionWith(SubscriptionStatus status) {
        return BillingSubscription.builder().id(20L).status(status).build();
    }

    @Test
    void updatePaymentMethod_trial_savesNewMethod() {
        BillingAccount account = account();
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(account));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscriptionWith(SubscriptionStatus.TRIAL)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updatePaymentMethod(USER_ID, PaymentMethodType.CARD);

        ArgumentCaptor<BillingAccount> captor = ArgumentCaptor.forClass(BillingAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMethod()).isEqualTo(PaymentMethodType.CARD);
    }

    @Test
    void updatePaymentMethod_pastDue_throwsRuleException() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(account()));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscriptionWith(SubscriptionStatus.PAST_DUE)));

        assertThatThrownBy(() -> service.updatePaymentMethod(USER_ID, PaymentMethodType.PIX))
                .isInstanceOf(RuleException.class);

        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = SubscriptionStatus.class, names = {"ACTIVE", "CANCELED", "PAST_DUE"})
    void updatePaymentMethod_nonTrial_throwsRuleException(SubscriptionStatus status) {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(account()));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscriptionWith(status)));

        assertThatThrownBy(() -> service.updatePaymentMethod(USER_ID, PaymentMethodType.PIX))
                .isInstanceOf(RuleException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void updatePaymentMethod_billingAccountNotFound_throwsNotFoundException() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePaymentMethod(USER_ID, PaymentMethodType.PIX))
                .isInstanceOf(NotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void updatePaymentMethod_subscriptionNotFound_throwsNotFoundException() {
        when(repository.findByUserId(USER_ID)).thenReturn(Optional.of(account()));
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePaymentMethod(USER_ID, PaymentMethodType.CARD))
                .isInstanceOf(NotFoundException.class);

        verify(repository, never()).save(any());
    }
}
