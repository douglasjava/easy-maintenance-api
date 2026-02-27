package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.response.MySubscriptionStatusResponse;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingQueryService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    private static final Set<SubscriptionStatus> BLOCKED_STATUSES = Set.of(
            SubscriptionStatus.PAST_DUE,
            SubscriptionStatus.BLOCKED,
            SubscriptionStatus.CANCELED
    );

    @Transactional(readOnly = true)
    public MySubscriptionStatusResponse getMySubscriptionStatus() {

        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        log.info("Fetching subscription status for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException(String.format("Usuário %s não encontrado", email)));

        return userSubscriptionRepository.findByUserId(user.getId())
                .map(sub -> {
                    LocalDate nextInvoiceDate = null;
                    String paymentLink = null;

                    var openInvoiceOpt = invoiceRepository.findFirstByPayerIdAndStatusOrderByCreatedAtDesc(user.getId(), InvoiceStatus.OPEN);
                    if (openInvoiceOpt.isPresent()) {
                        Invoice invoice = openInvoiceOpt.get();
                        nextInvoiceDate = invoice.getDueDate();

                        paymentLink = paymentRepository.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoice.getId(), PaymentStatus.PENDING)
                                .map(Payment::getPaymentLink)
                                .orElse(null);
                    }

                    return MySubscriptionStatusResponse.builder()
                            .status(sub.getStatus())
                            .trialEndsAt(sub.getTrialEndsAt())
                            .isBlocked(BLOCKED_STATUSES.contains(sub.getStatus()))
                            .nextInvoiceDate(nextInvoiceDate)
                            .paymentLink(paymentLink)
                            .build();
                })
                .orElseGet(() -> MySubscriptionStatusResponse.builder()
                        .status(SubscriptionStatus.NONE)
                        .trialEndsAt(null)
                        .isBlocked(false)
                        .nextInvoiceDate(null)
                        .paymentLink(null)
                        .build());

    }

}
