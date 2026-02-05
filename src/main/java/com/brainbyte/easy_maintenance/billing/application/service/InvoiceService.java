package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.OrganizationSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository repository;
    private final OrganizationSubscriptionRepository subscriptionRepository;
    private final BillingAccountRepository billingAccountRepository;

    public InvoiceDTO.BillingSummaryResponse getSummary(Long userId) {
        var account = billingAccountRepository.findByUserId(userId)
                .map(IBillingMapper.INSTANCE::toBillingAccountResponse)
                .orElse(null);

        var currentInvoice = repository.findFirstByPayerIdAndStatusOrderByCreatedAtDesc(userId, InvoiceStatus.OPEN)
                .map(IBillingMapper.INSTANCE::toInvoiceResponse)
                .orElse(null);

        return new InvoiceDTO.BillingSummaryResponse(account, currentInvoice);
    }

    public PageResponse<InvoiceDTO.InvoiceResponse> listInvoices(Long userId, Pageable pageable) {
        var page = repository.findAllByPayerId(userId, pageable)
                .map(IBillingMapper.INSTANCE::toInvoiceResponse);
        return PageResponse.of(page);
    }

    @Transactional
    public void generateInvoices(LocalDate periodStart, LocalDate periodEnd) {
        log.info("Generating invoices for period {} to {}", periodStart, periodEnd);

        var activeSubscriptions = subscriptionRepository.findAllByStatusIn(List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL));

        var subscriptionsByPayer = activeSubscriptions.stream()
                .collect(Collectors.groupingBy(s -> s.getPayer().getId()));

        for (Map.Entry<Long, List<OrganizationSubscription>> entry : subscriptionsByPayer.entrySet()) {
            Long payerId = entry.getKey();
            List<OrganizationSubscription> payerSubscriptions = entry.getValue();

            if (repository.findByPayerIdAndPeriodStartAndPeriodEnd(payerId, periodStart, periodEnd).isPresent()) {
                log.info("Invoice already exists for payer {} and period {} to {}. Skipping.", payerId, periodStart, periodEnd);
                continue;
            }

            var payer = payerSubscriptions.getFirst().getPayer();
            
            Invoice invoice = Invoice.builder()
                    .payer(payer)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .dueDate(periodEnd.plusDays(5))
                    .status(InvoiceStatus.OPEN)
                    .currency("BRL")
                    .items(new ArrayList<>())
                    .build();

            int subtotal = 0;
            for (var sub : payerSubscriptions) {

                var plan = sub.getPlan();
                var item = InvoiceItem.builder()
                        .invoice(invoice)
                        .organization(sub.getOrganization())
                        .plan(sub.getPlan())
                        .description("Assinatura Plano " + plan.getName() + " - Org: " + sub.getOrganization().getName())
                        .quantity(1)
                        .unitAmountCents(plan.getPriceCents())
                        .amountCents(plan.getPriceCents())
                        .build();
                
                invoice.getItems().add(item);
                subtotal += item.getAmountCents();
            }

            invoice.setSubtotalCents(subtotal);
            invoice.setDiscountCents(0);
            invoice.setTotalCents(subtotal);

            repository.save(invoice);
        }
    }

    public PageResponse<InvoiceDTO.InvoiceResponse> listAllInvoices(
            InvoiceStatus status, LocalDate periodStart, LocalDate periodEnd, Long payerUserId, Pageable pageable) {

        var page = repository.findAllFiltered(status, periodStart, periodEnd, payerUserId, pageable)
                .map(IBillingMapper.INSTANCE::toInvoiceResponse);

        return PageResponse.of(page);

    }

    public InvoiceDTO.InvoiceResponse getInvoiceById(Long invoiceId) {

        return repository.findByIdFetchItems(invoiceId)
                .map(IBillingMapper.INSTANCE::toInvoiceResponse)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));

    }
}
