package com.brainbyte.easy_maintenance.billing.infrastructure.persistence;

import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPayerIdAndPeriodStartAndPeriodEnd(Long payerUserId, LocalDate periodStart, LocalDate periodEnd);

    Optional<Invoice> findFirstByPayerIdOrderByPeriodEndDesc(Long payerUserId);

    @EntityGraph(attributePaths = {"items", "payer"})
    Optional<Invoice> findFirstByPayerIdAndStatusOrderByCreatedAtDesc(Long payerUserId, InvoiceStatus status);

    Page<Invoice> findAllByPayerId(Long payerUserId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "payer"})
    @Query("SELECT i FROM Invoice i " +
            "JOIN FETCH i.payer p " +
            "WHERE (:status IS NULL OR i.status = :status) " +
            "AND (:periodStart IS NULL OR i.periodStart >= :periodStart) " +
            "AND (:periodEnd IS NULL OR i.periodEnd <= :periodEnd) " +
            "AND (:dueDateStart IS NULL OR i.dueDate >= :dueDateStart) " +
            "AND (:dueDateEnd IS NULL OR i.dueDate <= :dueDateEnd) " +
            "AND (:payerUserId IS NULL OR p.id = :payerUserId)")
    Page<Invoice> findAllFiltered(
            @Param("status") InvoiceStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("dueDateStart") LocalDate dueDateStart,
            @Param("dueDateEnd") LocalDate dueDateEnd,
            @Param("payerUserId") Long payerUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"items", "payer"})
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdFetchItems(@Param("id") Long id);

    @Query("SELECT i FROM Invoice i " +
            "WHERE i.payer.id = :payerUserId " +
            "ORDER BY i.createdAt DESC")
    Page<Invoice> findAllByPayerUserIdOrderByCreatedAtDesc(@Param("payerUserId") Long payerUserId, Pageable pageable);

    @Query("""
        SELECT i FROM Invoice i
        WHERE i.payer.id = :payerUserId
        ORDER BY i.createdAt DESC
    """)
    List<Invoice> findRecentInvoices(@Param("payerUserId") Long payerUserId, Pageable pageable);

}
