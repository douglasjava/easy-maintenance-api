package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long> {

    Optional<BillingAccount> findByUserId(Long userId);

    Optional<BillingAccount> findByExternalCustomerId(String externalCustomerId);

    @Query(value = """
            SELECT new com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse(
                u.id,
                u.name,
                u.email,
                COUNT(DISTINCT s),
                CAST(SUM(COALESCE(p.priceCents, 0)) AS long),
                CAST(COALESCE(up.priceCents, 0) AS long)
            )
            FROM User u
            LEFT JOIN OrganizationSubscription s ON s.payer = u
            LEFT JOIN s.plan p
            LEFT JOIN UserSubscription us ON us.user = u
            LEFT JOIN us.plan up
            GROUP BY u.id, u.name, u.email, up.priceCents
            HAVING COUNT(s) > 0 OR COUNT(us) > 0
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u)
            FROM User u
            LEFT JOIN OrganizationSubscription s ON s.payer = u
            LEFT JOIN UserSubscription us ON us.user = u
            WHERE s.id IS NOT NULL OR us.id IS NOT NULL
            """)
    Page<PayerSummaryResponse> findPayersSummary(Pageable pageable);

    @Query("""
            SELECT new com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse(
                u.id,
                u.name,
                u.email,
                COUNT(DISTINCT s),
                CAST(SUM(COALESCE(p.priceCents, 0)) AS long),
                CAST(COALESCE(up.priceCents, 0) AS long)
            )
            FROM User u
            LEFT JOIN OrganizationSubscription s ON s.payer = u
            LEFT JOIN s.plan p
            LEFT JOIN UserSubscription us ON us.user = u
            LEFT JOIN us.plan up
            GROUP BY u.id, u.name, u.email, up.priceCents
            HAVING COUNT(s) > 0 OR COUNT(us) > 0
            ORDER BY (SUM(COALESCE(p.priceCents, 0)) + COALESCE(up.priceCents, 0)) DESC
            """)
    List<PayerSummaryResponse> findTopPayers();
}
