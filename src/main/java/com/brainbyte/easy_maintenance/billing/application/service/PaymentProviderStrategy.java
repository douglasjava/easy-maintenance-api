package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.Payment;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.billing.application.dto.PaymentResponse;

public interface PaymentProviderStrategy {

    PaymentProvider getProvider();

    PaymentResponse createPayment(Payment payment);

}
