package com.brainbyte.easy_maintenance.payment.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.payment.application.dto.CustomerDTO;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.application.dto.PaymentResponse;

public interface PaymentProviderStrategy {

    PaymentProvider getProvider();

    PaymentResponse createPayment(Payment payment);

    String createExternalCustomer(CustomerDTO customerDTO);

}
