package com.brainbyte.easy_maintenance.payment.infrastructure.persistence;

import com.brainbyte.easy_maintenance.payment.domain.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findAllByUserId(Long userId);

    Optional<PaymentMethod> findByUserIdAndIsDefaultTrue(Long userId);

    List<PaymentMethod> findAllByUserIdAndIsDefaultFalse(Long userId);
}
