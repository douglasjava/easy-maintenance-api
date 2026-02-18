package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.OrganizationSubscription;
import com.brainbyte.easy_maintenance.billing.domain.UserSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.OrganizationSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.billing.mapper.InvoiceMapper;
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
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final BillingAccountRepository billingAccountRepository;

    public InvoiceDTO.BillingSummaryResponse getSummary(Long userId) {
        var account = billingAccountRepository.findByUserId(userId)
                .map(IBillingMapper.INSTANCE::toBillingAccountResponse)
                .orElse(null);

        var currentInvoice = repository.findFirstByPayerIdAndStatusOrderByCreatedAtDesc(userId, InvoiceStatus.OPEN)
                .map(InvoiceMapper.INSTANCE::toInvoiceResponse)
                .orElse(null);

        return new InvoiceDTO.BillingSummaryResponse(account, currentInvoice);
    }

    public PageResponse<InvoiceDTO.InvoiceResponse> listInvoices(Long userId, Pageable pageable) {
        var page = repository.findAllByPayerId(userId, pageable)
                .map(InvoiceMapper.INSTANCE::toInvoiceResponse);
        return PageResponse.of(page);
    }

    @Transactional
    public void generateInvoices(LocalDate periodStart, LocalDate periodEnd) {
        log.info("Generating invoices for period {} to {}", periodStart, periodEnd);

        var statusList = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL);
        var activeOrgSubscriptions = subscriptionRepository.findAllByStatusIn(statusList);
        var activeUserSubscriptions = userSubscriptionRepository.findAllByStatusIn(statusList);

        var orgSubsByPayer = activeOrgSubscriptions.stream()
                .collect(Collectors.groupingBy(s -> s.getPayer().getId()));

        var userSubsByPayer = activeUserSubscriptions.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        var allPayerIds = new java.util.HashSet<Long>();
        allPayerIds.addAll(orgSubsByPayer.keySet());
        allPayerIds.addAll(userSubsByPayer.keySet());

        for (Long payerId : allPayerIds) {
            if (repository.findByPayerIdAndPeriodStartAndPeriodEnd(payerId, periodStart, periodEnd).isPresent()) {
                log.info("Invoice already exists for payer {} and period {} to {}. Skipping.", payerId, periodStart, periodEnd);
                continue;
            }

            var userSub = userSubsByPayer.get(payerId);
            var orgSubs = orgSubsByPayer.getOrDefault(payerId, List.of());

            var payer = userSub != null ? userSub.getUser() : orgSubs.getFirst().getPayer();

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

            if (userSub != null) {
                var plan = userSub.getPlan();
                var item = InvoiceItem.builder()
                        .invoice(invoice)
                        .plan(plan)
                        .description("Assinatura Usuário - Plano: " + plan.getName())
                        .quantity(1)
                        .unitAmountCents(plan.getPriceCents())
                        .amountCents(plan.getPriceCents())
                        .build();
                invoice.getItems().add(item);
                subtotal += item.getAmountCents();
            }

            for (var sub : orgSubs) {
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
                .map(InvoiceMapper.INSTANCE::toInvoiceResponse);

        return PageResponse.of(page);

    }

    public InvoiceDTO.InvoiceResponse getInvoiceById(Long invoiceId) {

        return repository.findByIdFetchItems(invoiceId)
                .map(InvoiceMapper.INSTANCE::toInvoiceResponse)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + invoiceId));

    }
}
