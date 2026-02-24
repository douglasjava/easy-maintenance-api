package com.brainbyte.easy_maintenance.payment.application.factory;

import com.brainbyte.easy_maintenance.payment.application.service.PaymentProviderStrategy;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentProviderFactory {

    private final Map<PaymentProvider, PaymentProviderStrategy> providers;

    public PaymentProviderFactory(List<PaymentProviderStrategy> strategies) {
        this.providers = strategies.stream()
                .collect(Collectors.toMap(PaymentProviderStrategy::getProvider, Function.identity()));
    }

    public PaymentProviderStrategy get(PaymentProvider provider) {
        return Optional.ofNullable(providers.get(provider))
                .orElseThrow(() -> new IllegalArgumentException("Provider não suportado: " + provider));
    }

}
