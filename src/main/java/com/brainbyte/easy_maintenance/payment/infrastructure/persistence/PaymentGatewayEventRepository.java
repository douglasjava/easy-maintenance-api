package com.brainbyte.easy_maintenance.payment.infrastructure.persistence;

import com.brainbyte.easy_maintenance.payment.domain.PaymentGatewayEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentGatewayEventRepository extends JpaRepository<PaymentGatewayEvent, Long> {
}
