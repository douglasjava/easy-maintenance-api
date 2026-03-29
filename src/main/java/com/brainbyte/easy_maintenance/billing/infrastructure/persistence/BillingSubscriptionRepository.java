package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription, Long> {

    Optional<BillingSubscription> findByBillingAccountUserId(Long userId);

    @Query("SELECT s FROM BillingSubscription s " +
            "JOIN FETCH s.billingAccount ba " +
            "JOIN FETCH ba.user " +
            "WHERE ba.user.id IN :userIds")
    List<BillingSubscription> findAllByBillingAccountUserIdIn(@Param("userIds") List<Long> userIds);

    Optional<BillingSubscription> findByExternalSubscriptionId(String externalSubscriptionId);

    @Query("SELECT COALESCE(SUM(s.totalCents), 0) FROM BillingSubscription s WHERE s.status = 'ACTIVE'")
    Long sumActiveTotalCents();

    @Query("SELECT s FROM BillingSubscription s " +
            "WHERE s.status IN :statusList " +
            "AND (s.status <> com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus.TRIAL " +
            "OR (s.currentPeriodEnd IS NOT NULL AND s.currentPeriodEnd < :trialEndsBefore))")
    List<BillingSubscription> findEligibleForInvoicing(
            @Param("statusList") List<SubscriptionStatus> statusList,
            @Param("trialEndsBefore") Instant trialEndsBefore
    );

    @Query("SELECT s FROM BillingSubscription s " +
            "WHERE s.status = 'PAST_DUE' " +
            "AND s.updatedAt <= :limitDate")
    List<BillingSubscription> findEligibleForBlocking(@Param("limitDate") Instant limitDate);

    List<BillingSubscription> findAllByNextDueDate(LocalDate nextDueDate);

}
