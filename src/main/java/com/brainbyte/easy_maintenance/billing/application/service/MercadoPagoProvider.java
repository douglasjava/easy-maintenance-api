package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.domain.Payment;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.billing.application.dto.PaymentResponse;
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
        // Implementação mock do Mercado Pago
        payment.setExternalPaymentId("mp-" + UUID.randomUUID());
        payment.setStatus(PaymentStatus.PENDING);
        
        if (payment.getMethodType() == com.brainbyte.easy_maintenance.billing.domain.enums.PaymentMethodType.PIX) {
            payment.setPixQrCode("mock-pix-qr-code");
            payment.setPixQrCodeBase64("mock-pix-base64");
        }
        
        return PaymentResponse.builder()
                .id(payment.getId())
                .invoiceId(payment.getInvoice().getId())
                .payerUserId(payment.getPayer().getId())
                .provider(payment.getProvider())
                .methodType(payment.getMethodType())
                .status(payment.getStatus())
                .amountCents(payment.getAmountCents())
                .currency(payment.getCurrency())
                .externalPaymentId(payment.getExternalPaymentId())
                .externalReference(payment.getExternalReference())
                .pixQrCode(payment.getPixQrCode())
                .pixQrCodeBase64(payment.getPixQrCodeBase64())
                .paymentLink(payment.getPaymentLink())
                .createdAt(Instant.now())
                .build();
    }
}
