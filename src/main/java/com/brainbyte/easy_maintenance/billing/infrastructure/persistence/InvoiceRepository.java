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
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByPayerIdAndPeriodStartAndPeriodEnd(Long payerUserId, LocalDate periodStart, LocalDate periodEnd);

    Optional<Invoice> findFirstByPayerIdAndStatusOrderByCreatedAtDesc(Long payerUserId, InvoiceStatus status);

    Page<Invoice> findAllByPayerId(Long payerUserId, Pageable pageable);

    @EntityGraph(attributePaths = "items")
    @Query("SELECT i FROM Invoice i " +
            "WHERE (:status IS NULL OR i.status = :status) " +
            "AND (:periodStart IS NULL OR i.periodStart >= :periodStart) " +
            "AND (:periodEnd IS NULL OR i.periodEnd <= :periodEnd) " +
            "AND (:payerUserId IS NULL OR i.payer.id = :payerUserId)")
    Page<Invoice> findAllFiltered(
            @Param("status") InvoiceStatus status,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("payerUserId") Long payerUserId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "items")
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findByIdFetchItems(@Param("id") Long id);

}
