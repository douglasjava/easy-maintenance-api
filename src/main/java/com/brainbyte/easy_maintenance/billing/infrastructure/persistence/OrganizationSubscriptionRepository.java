package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.domain.OrganizationSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, Long> {

    @EntityGraph(attributePaths = "payer")
    Optional<OrganizationSubscription> findByOrganizationCode(String organizationCode);

    List<OrganizationSubscription> findAllByStatusIn(List<SubscriptionStatus> statuses);

    @Query("SELECT s FROM OrganizationSubscription s " +
            "JOIN FETCH s.organization " +
            "JOIN FETCH s.payer " +
            "JOIN FETCH s.plan " +
            "WHERE (:status IS NULL OR s.status = :status) " +
            "AND (:planCode IS NULL OR s.plan.code = :planCode) " +
            "AND (:payerUserId IS NULL OR s.payer.id = :payerUserId) " +
            "AND (:query IS NULL OR (LOWER(s.organization.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(s.organization.code) LIKE LOWER(CONCAT('%', :query, '%'))))")
    List<OrganizationSubscription> findAllFiltered(
            @Param("status") SubscriptionStatus status,
            @Param("planCode") String planCode,
            @Param("payerUserId") Long payerUserId,
            @Param("query") String query
    );

    @Query("SELECT COUNT(s) FROM OrganizationSubscription s")
    Long countTotalOrganizations();

    @Query("SELECT COUNT(DISTINCT s.payer.id) FROM OrganizationSubscription s")
    Long countTotalPayers();

    @Query("SELECT SUM(p.priceCents) FROM OrganizationSubscription s JOIN s.plan p WHERE s.status = com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus.ACTIVE")
    Long sumEstimatedMonthlyRevenue();

    @Query("""
        SELECT p.name, COUNT(s)
        FROM OrganizationSubscription s
        JOIN s.plan p
        GROUP BY p.name
    """)
    List<Object[]> countByPlanName();

}
