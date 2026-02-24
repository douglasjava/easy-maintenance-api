package com.brainbyte.easy_maintenance.payment.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.payment.application.dto.CustomerDTO;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.application.dto.PaymentResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MercadoPagoProvider implements PaymentProviderStrategy {

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.MERCADO_PAGO;
    }

    @Override
    public PaymentResponse createPayment(Payment payment) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String createExternalCustomer(CustomerDTO customerDTO) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
