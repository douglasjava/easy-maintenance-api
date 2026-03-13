package com.brainbyte.easy_maintenance.payment.infrastructure.persistence;

import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.Instant;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByExternalPaymentId(String externalPaymentId);

    Optional<Payment> findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(Long invoiceId, PaymentStatus status);

    Optional<Payment> findFirstByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    List<Payment> findByPayerIdOrderByCreatedAtDesc(Long payerId, Pageable pageable);

    static Specification<Payment> hasPayerUserId(Long payerUserId) {
        return (root, query, cb) -> payerUserId == null ? null : cb.equal(root.get("payer").get("id"), payerUserId);
    }

    static Specification<Payment> hasStatus(PaymentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Payment> hasProvider(PaymentProvider provider) {
        return (root, query, cb) -> provider == null ? null : cb.equal(root.get("provider"), provider);
    }

    static Specification<Payment> createdBetween(Instant start, Instant end) {
        return (root, query, cb) -> {
            if (start == null && end == null) return null;
            if (start != null && end != null) return cb.between(root.get("createdAt"), start, end);
            if (start != null) return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            return cb.lessThanOrEqualTo(root.get("createdAt"), end);
        };
    }
}
