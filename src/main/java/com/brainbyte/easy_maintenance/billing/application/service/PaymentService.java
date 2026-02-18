package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.CreatePaymentRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.PaymentResponse;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.Payment;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.billing.mapper.PaymentMapper;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.brainbyte.easy_maintenance.billing.infrastructure.persistence.PaymentRepository.*;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final List<PaymentProviderStrategy> providerStrategies;

    public PageResponse<PaymentResponse> listPayments(
            Long userId, 
            Instant startDate, 
            Instant endDate, 
            PaymentStatus status, 
            PaymentProvider provider, 
            Pageable pageable) {

        Specification<Payment> spec = Specification.where(hasPayerUserId(userId))
                .and(createdBetween(startDate, endDate))
                .and(hasStatus(status))
                .and(hasProvider(provider));

        var page = paymentRepository.findAll(spec, pageable)
                .map(PaymentMapper.INSTANCE::toResponse);

        return PageResponse.of(page);
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(request.invoiceId())
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + request.invoiceId()));

        User payer = userRepository.findById(request.payerUserId())
                .orElseThrow(() -> new NotFoundException("User not found: " + request.payerUserId()));

        PaymentProviderStrategy strategy = findStrategy(request.provider());

        Payment payment = Payment.builder()
                .invoice(invoice)
                .payer(payer)
                .provider(request.provider())
                .methodType(request.methodType())
                .status(PaymentStatus.PENDING)
                .amountCents(request.amountCents())
                .currency(request.currency() != null ? request.currency() : "BRL")
                .externalReference(request.externalReference())
                .build();

        payment = paymentRepository.save(payment);

        return strategy.createPayment(payment);
    }

    private PaymentProviderStrategy findStrategy(PaymentProvider provider) {
        return providerStrategies.stream()
                .filter(s -> s.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No strategy found for provider: " + provider));
    }
}
