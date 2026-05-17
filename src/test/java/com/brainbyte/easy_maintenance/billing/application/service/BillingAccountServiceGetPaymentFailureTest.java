package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.error.RefusalBucket;
import com.brainbyte.easy_maintenance.billing.error.RefusalReasonClassifier;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingAccountServiceGetPaymentFailureTest {

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
    private static final Long SUB_ID = 20L;

    private BillingSubscription subscription() {
        return BillingSubscription.builder().id(SUB_ID).build();
    }

    @Test
    void getLastPaymentFailure_noSubscription_returnsEmptyResponse() {
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID)).thenReturn(Optional.empty());

        BillingAccountDTO.PaymentFailureResponse result = service.getLastPaymentFailure(USER_ID);

        assertThat(result.failureReason()).isNull();
        assertThat(result.bucket()).isNull();
        assertThat(result.failedAt()).isNull();
    }

    @Test
    void getLastPaymentFailure_subscriptionExistsNoFailedPayment_returnsEmptyResponse() {
        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription()));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(SUB_ID, PaymentStatus.FAILED))
                .thenReturn(Optional.empty());

        BillingAccountDTO.PaymentFailureResponse result = service.getLastPaymentFailure(USER_ID);

        assertThat(result.failureReason()).isNull();
        assertThat(result.bucket()).isNull();
        assertThat(result.failedAt()).isNull();
    }

    @Test
    void getLastPaymentFailure_failedPaymentWithReason_returnsClassifiedResponse() {
        Instant failedAt = Instant.parse("2026-05-10T12:00:00Z");
        Payment payment = Payment.builder()
                .failureReason("INSUFFICIENT_FUNDS")
                .updatedAt(failedAt)
                .build();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription()));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(SUB_ID, PaymentStatus.FAILED))
                .thenReturn(Optional.of(payment));
        when(refusalReasonClassifier.classify("INSUFFICIENT_FUNDS")).thenReturn(RefusalBucket.USER_ACTION);

        BillingAccountDTO.PaymentFailureResponse result = service.getLastPaymentFailure(USER_ID);

        assertThat(result.failureReason()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(result.bucket()).isEqualTo("USER_ACTION");
        assertThat(result.failedAt()).isEqualTo(failedAt.toString());
    }

    @Test
    void getLastPaymentFailure_failedPaymentNullReason_returnsUnknownBucket() {
        Payment payment = Payment.builder()
                .failureReason(null)
                .updatedAt(Instant.now())
                .build();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription()));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(SUB_ID, PaymentStatus.FAILED))
                .thenReturn(Optional.of(payment));
        when(refusalReasonClassifier.classify(null)).thenReturn(RefusalBucket.UNKNOWN);

        BillingAccountDTO.PaymentFailureResponse result = service.getLastPaymentFailure(USER_ID);

        assertThat(result.failureReason()).isNull();
        assertThat(result.bucket()).isEqualTo("UNKNOWN");
    }

    @Test
    void getLastPaymentFailure_failedPaymentNullUpdatedAt_returnsNullFailedAt() {
        Payment payment = Payment.builder()
                .failureReason("NO_FUNDS")
                .updatedAt(null)
                .build();

        when(billingSubscriptionRepository.findByBillingAccountUserId(USER_ID))
                .thenReturn(Optional.of(subscription()));
        when(paymentRepository.findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(SUB_ID, PaymentStatus.FAILED))
                .thenReturn(Optional.of(payment));
        when(refusalReasonClassifier.classify("NO_FUNDS")).thenReturn(RefusalBucket.TRANSIENT);

        BillingAccountDTO.PaymentFailureResponse result = service.getLastPaymentFailure(USER_ID);

        assertThat(result.bucket()).isEqualTo("TRANSIENT");
        assertThat(result.failedAt()).isNull();
    }
}
