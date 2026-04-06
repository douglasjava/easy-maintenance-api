package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long>, JpaSpecificationExecutor<BillingAccount> {

    Optional<BillingAccount> findByUserId(Long userId);

    Optional<BillingAccount> findByExternalCustomerId(String externalCustomerId);

    @Query(value = """
            SELECT new com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse(
                u.id,
                u.name,
                u.email,
                COUNT(DISTINCT CASE WHEN i.sourceType = 'ORGANIZATION' THEN i.id END),
                CAST(SUM(COALESCE(CASE WHEN i.sourceType = 'ORGANIZATION' THEN p.priceCents END, 0)) AS long),
                CAST(COALESCE(SUM(CASE WHEN i.sourceType = 'USER' THEN p.priceCents END), 0) AS long)
            )
            FROM User u
            JOIN BillingAccount ba ON ba.user = u
            JOIN BillingSubscription s ON s.billingAccount = ba
            JOIN BillingSubscriptionItem i ON i.billingSubscription = s
            JOIN i.plan p
            GROUP BY u.id, u.name, u.email
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u)
            FROM User u
            JOIN BillingAccount ba ON ba.user = u
            JOIN BillingSubscription s ON s.billingAccount = ba
            """)
    Page<PayerSummaryResponse> findPayersSummary(Pageable pageable);

    @Query("""
            SELECT new com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse(
                u.id,
                u.name,
                u.email,
                COUNT(DISTINCT CASE WHEN i.sourceType = 'ORGANIZATION' THEN i.id END),
                CAST(SUM(COALESCE(CASE WHEN i.sourceType = 'ORGANIZATION' THEN p.priceCents END, 0)) AS long),
                CAST(COALESCE(SUM(CASE WHEN i.sourceType = 'USER' THEN p.priceCents END), 0) AS long)
            )
            FROM User u
            JOIN BillingAccount ba ON ba.user = u
            JOIN BillingSubscription s ON s.billingAccount = ba
            JOIN BillingSubscriptionItem i ON i.billingSubscription = s
            JOIN i.plan p
            GROUP BY u.id, u.name, u.email
            ORDER BY (SUM(COALESCE(p.priceCents, 0))) DESC
            """)
    List<PayerSummaryResponse> findTopPayers();
}
