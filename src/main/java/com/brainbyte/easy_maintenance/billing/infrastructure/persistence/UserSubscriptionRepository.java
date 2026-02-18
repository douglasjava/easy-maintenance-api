package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.domain.UserSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<UserSubscription> findByUserId(Long userId);

    @EntityGraph(attributePaths = "plan")
    List<UserSubscription> findAllByUserIdIn(List<Long> userIds);

    List<UserSubscription> findAllByStatusIn(List<SubscriptionStatus> statuses);

    @Query("SELECT s FROM UserSubscription s " +
            "JOIN FETCH s.user " +
            "JOIN FETCH s.plan " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (:planCode IS NULL OR s.plan.code = :planCode) " +
            "AND (:userId IS NULL OR s.user.id = :userId)")
    List<UserSubscription> findAllFiltered(
            @Param("status") SubscriptionStatus status,
            @Param("planCode") String planCode,
            @Param("userId") Long userId
    );

    @Query(value = """
        select sum(bp.price_cents) from user_subscriptions os
        inner join billing_plans bp on os.plan_code = bp.code
        where os.status = "ACTIVE"
    """, nativeQuery = true)
    Long totalPriceUsersActive();

}
