package com.brainbyte.easy_maintenance.payment.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.IAsaasMapper;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.payment.application.dto.CustomerDTO;
import com.brainbyte.easy_maintenance.payment.application.dto.PaymentResponse;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsaasProvider implements PaymentProviderStrategy {

    private final AsaasClient asaasClient;

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.ASAAS;
    }

    @Override
    public PaymentResponse createPayment(Payment payment) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String createExternalCustomer(CustomerDTO customerDTO) {

        log.info("Criando usuário no provedor Asaas usuário {}", customerDTO.getEmail());

        var customer = IAsaasMapper.INSTANCE.toCreateCustomerRequest(customerDTO);
        var customerResponse = asaasClient.createCustomer(customer);

        log.info("Usuário criado {}", customerResponse.id());

        return customerResponse.id();

    }

}
