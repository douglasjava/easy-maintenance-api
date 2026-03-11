package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface BillingSubscriptionItemRepository extends JpaRepository<BillingSubscriptionItem, Long> {

    @Query("SELECT i FROM BillingSubscriptionItem i JOIN FETCH i.plan WHERE i.billingSubscription.id = :billingSubscriptionId")
    List<BillingSubscriptionItem> findAllByBillingSubscriptionIdFetchPlan(@Param("billingSubscriptionId") Long billingSubscriptionId);

    List<BillingSubscriptionItem> findAllByBillingSubscriptionIdIn(Collection<Long> billingSubscriptionIds);

    List<BillingSubscriptionItem> findAllByBillingSubscriptionId(Long billingSubscriptionId);

    @Query("SELECT i FROM BillingSubscriptionItem i " +
            "JOIN FETCH i.plan " +
            "LEFT JOIN FETCH i.nextPlan " +
            "WHERE i.sourceType = :sourceType AND i.sourceId IN :sourceIds")
    List<BillingSubscriptionItem> findAllBySourceTypeAndSourceIdIn(
            @Param("sourceType") BillingSubscriptionItemSourceType sourceType,
            @Param("sourceIds") Collection<String> sourceIds);

}
