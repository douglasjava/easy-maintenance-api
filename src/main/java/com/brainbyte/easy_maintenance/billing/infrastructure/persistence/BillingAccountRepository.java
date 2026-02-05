package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingAccountRepository extends JpaRepository<BillingAccount, Long> {

    Optional<BillingAccount> findByUserId(Long userId);

    @Query("""
            SELECT new com.brainbyte.easy_maintenance.billing.application.dto.response.PayerSummaryResponse(
                u.id,
                u.name,
                u.email,
                COUNT(s),
                SUM(p.priceCents)
            )
            FROM User u
            JOIN OrganizationSubscription s ON s.payer = u
            JOIN s.plan p
            GROUP BY u.id, u.name, u.email
            ORDER BY SUM(p.priceCents) DESC
            """)
    List<PayerSummaryResponse> findTopPayers();
}
