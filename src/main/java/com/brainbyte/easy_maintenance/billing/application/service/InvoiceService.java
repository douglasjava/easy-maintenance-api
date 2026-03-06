package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.billing.mapper.InvoiceMapper;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository repository;
    private final BillingAccountRepository billingAccountRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionItemRepository itemRepository;

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
    public List<Invoice> generateInvoices(LocalDate periodStart, LocalDate periodEnd, List<SubscriptionStatus> statusList, Instant trialEndsBefore) {
        log.info("Starting invoice generation for period {} to {} (statuses: {}, trialEndsBefore: {})", 
                periodStart, periodEnd, statusList, trialEndsBefore);

        var billingSubscriptions = fetchBillingSubscriptions(statusList, trialEndsBefore);

        log.info("Found {} billing subscriptions to process", billingSubscriptions.size());

        var createdInvoices = new ArrayList<Invoice>();

        for (var billingSubscription : billingSubscriptions) {
            processPayerInvoice(billingSubscription, periodStart, periodEnd).ifPresent(createdInvoices::add);
        }

        log.info("Finished invoice generation. Total invoices created: {}", createdInvoices.size());

        return createdInvoices;
    }

    @Transactional
    public Optional<Invoice> generateInvoiceForPayer(Long payerId, LocalDate start, LocalDate end) {
        log.info("Generating invoice for payer {} and period {} to {}", payerId, start, end);
        
        var billingSubscription = billingSubscriptionRepository.findByBillingAccountUserId(payerId)
                .orElseThrow(() -> new NotFoundException("Billing subscription not found for payer: " + payerId));

        return processPayerInvoice(billingSubscription, start, end);
    }

    private List<BillingSubscription> fetchBillingSubscriptions(List<SubscriptionStatus> statusList, Instant trialEndsBefore) {
        return billingSubscriptionRepository.findEligibleForInvoicing(statusList, trialEndsBefore);
    }

    private Optional<Invoice> processPayerInvoice(BillingSubscription billingSubscription, LocalDate periodStart, LocalDate periodEnd) {
        Long payerId = billingSubscription.getBillingAccount().getUser().getId();
        
        if (repository.findByPayerIdAndPeriodStartAndPeriodEnd(payerId, periodStart, periodEnd).isPresent()) {
            log.info("Invoice already exists for payer {} and period {} to {}. Skipping.", payerId, periodStart, periodEnd);
            return Optional.empty();
        }

        log.debug("Creating invoice for payer {} from billing subscription {}", payerId, billingSubscription.getId());

        Invoice invoice = Invoice.builder()
                .payer(billingSubscription.getBillingAccount().getUser())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .dueDate(periodEnd.plusDays(5))
                .status(InvoiceStatus.OPEN)
                .currency("BRL")
                .items(new ArrayList<>())
                .build();

        List<BillingSubscriptionItem> bItems = itemRepository.findAllByBillingSubscriptionId(billingSubscription.getId());
        
        long subtotal = 0;
        for (var bItem : bItems) {
            var invoiceItem = InvoiceItem.builder()
                    .invoice(invoice)
                    .plan(bItem.getPlan())
                    .description(generateItemDescription(bItem))
                    .quantity(1)
                    .unitAmountCents(bItem.getValueCents().intValue())
                    .amountCents(bItem.getValueCents().intValue())
                    .build();
            
            invoice.getItems().add(invoiceItem);
            subtotal += invoiceItem.getAmountCents();
        }

        invoice.setSubtotalCents((int) subtotal);
        invoice.setDiscountCents(0);
        invoice.setTotalCents((int) subtotal);

        Invoice savedInvoice = repository.save(invoice);
        log.info("Saved invoice {} for payer {} with total amount {} cents", savedInvoice.getId(), payerId, subtotal);
        
        return Optional.of(savedInvoice);
    }

    private String generateItemDescription(BillingSubscriptionItem bItem) {
        String type = bItem.getSourceType() == BillingSubscriptionItemSourceType.USER ? "Usuário" : "Organização";
        return String.format("Assinatura %s - Plano: %s (%s)", type, bItem.getPlan().getName(), bItem.getSourceId());
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
