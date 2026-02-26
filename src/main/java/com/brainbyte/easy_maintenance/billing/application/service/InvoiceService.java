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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
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
    public List<Invoice> generateInvoices(LocalDate periodStart, LocalDate periodEnd, List<SubscriptionStatus> statusList, Instant trialEndsBefore) {
        log.info("Starting invoice generation for period {} to {} (statuses: {}, trialEndsBefore: {})", 
                periodStart, periodEnd, statusList, trialEndsBefore);

        var activeOrgSubscriptions = fetchOrgSubscriptions(statusList, trialEndsBefore);
        var activeUserSubscriptions = fetchUserSubscriptions(statusList, trialEndsBefore);

        log.info("Found {} organization subscriptions and {} user subscriptions to process", 
                activeOrgSubscriptions.size(), activeUserSubscriptions.size());

        var orgSubsByPayer = activeOrgSubscriptions.stream()
                .collect(Collectors.groupingBy(s -> s.getPayer().getId()));

        var userSubsByPayer = activeUserSubscriptions.stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), s -> s));

        var allPayerIds = new HashSet<Long>();
        allPayerIds.addAll(orgSubsByPayer.keySet());
        allPayerIds.addAll(userSubsByPayer.keySet());

        var createdInvoices = new ArrayList<Invoice>();

        for (Long payerId : allPayerIds) {
            processPayerInvoice(payerId, periodStart, periodEnd, orgSubsByPayer, userSubsByPayer)
                    .ifPresent(createdInvoices::add);
        }

        log.info("Finished invoice generation. Total invoices created: {}", createdInvoices.size());

        return createdInvoices;

    }

    @Transactional
    public java.util.Optional<Invoice> generateInvoiceForPayer(Long payerId, LocalDate start, LocalDate end) {
        log.info("Generating invoice for payer {} and period {} to {}", payerId, start, end);
        
        var statusList = List.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL);
        
        var orgSubs = subscriptionRepository.findAllByStatusIn(statusList).stream()
                .filter(s -> s.getPayer().getId().equals(payerId))
                .collect(Collectors.toList());
        
        var userSub = userSubscriptionRepository.findAllByStatusIn(statusList).stream()
                .filter(s -> s.getUser().getId().equals(payerId))
                .findFirst()
                .orElse(null);

        if (orgSubs.isEmpty() && userSub == null) {
            log.warn("No active/trial subscriptions found for payer {}. Cannot generate invoice.", payerId);
            return java.util.Optional.empty();
        }

        var orgSubsByPayer = Map.of(payerId, orgSubs);
        var userSubsByPayer = userSub != null ? Map.of(payerId, userSub) : Map.<Long, UserSubscription>of();

        return processPayerInvoice(payerId, start, end, orgSubsByPayer, userSubsByPayer);
    }

    private List<OrganizationSubscription> fetchOrgSubscriptions(List<SubscriptionStatus> statusList, Instant trialEndsBefore) {
        var subscriptions = statusList.contains(SubscriptionStatus.TRIAL) && trialEndsBefore != null
                ? subscriptionRepository.findAllByStatusAndTrialEndsAtBefore(SubscriptionStatus.TRIAL, trialEndsBefore)
                : subscriptionRepository.findAllByStatusIn(statusList);

        if (statusList.contains(SubscriptionStatus.ACTIVE) && trialEndsBefore != null) {
            subscriptions.addAll(subscriptionRepository.findAllByStatusIn(List.of(SubscriptionStatus.ACTIVE)));
        }
        return subscriptions;
    }

    private List<UserSubscription> fetchUserSubscriptions(List<SubscriptionStatus> statusList, Instant trialEndsBefore) {
        var subscriptions = statusList.contains(SubscriptionStatus.TRIAL) && trialEndsBefore != null
                ? userSubscriptionRepository.findAllByStatusAndTrialEndsAtBefore(SubscriptionStatus.TRIAL, trialEndsBefore)
                : userSubscriptionRepository.findAllByStatusIn(statusList);

        if (statusList.contains(SubscriptionStatus.ACTIVE) && trialEndsBefore != null) {
            subscriptions.addAll(userSubscriptionRepository.findAllByStatusIn(List.of(SubscriptionStatus.ACTIVE)));
        }
        return subscriptions;
    }

    private java.util.Optional<Invoice> processPayerInvoice(
            Long payerId, 
            LocalDate periodStart, 
            LocalDate periodEnd, 
            Map<Long, List<OrganizationSubscription>> orgSubsByPayer, 
            Map<Long, UserSubscription> userSubsByPayer) {

        if (repository.findByPayerIdAndPeriodStartAndPeriodEnd(payerId, periodStart, periodEnd).isPresent()) {
            log.info("Invoice already exists for payer {} and period {} to {}. Skipping.", payerId, periodStart, periodEnd);
            return java.util.Optional.empty();
        }

        var userSub = userSubsByPayer.get(payerId);
        var orgSubs = orgSubsByPayer.getOrDefault(payerId, List.of());
        var payer = userSub != null ? userSub.getUser() : orgSubs.get(0).getPayer();

        log.debug("Creating invoice for payer {} ({} org subs, {} user sub)", 
                payerId, orgSubs.size(), userSub != null ? 1 : 0);

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
        subtotal += addUserSubscriptionItem(invoice, userSub);
        subtotal += addOrganizationSubscriptionItems(invoice, orgSubs);

        invoice.setSubtotalCents(subtotal);
        invoice.setDiscountCents(0);
        invoice.setTotalCents(subtotal);

        Invoice savedInvoice = repository.save(invoice);
        log.info("Saved invoice {} for payer {} with total amount {} cents", 
                savedInvoice.getId(), payerId, subtotal);
        
        return java.util.Optional.of(savedInvoice);
    }

    private int addUserSubscriptionItem(Invoice invoice, UserSubscription userSub) {
        if (userSub == null) return 0;

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
        return item.getAmountCents();
    }

    private int addOrganizationSubscriptionItems(Invoice invoice, List<OrganizationSubscription> orgSubs) {
        int total = 0;
        for (var sub : orgSubs) {
            var plan = sub.getPlan();
            var item = InvoiceItem.builder()
                    .invoice(invoice)
                    .organization(sub.getOrganization())
                    .plan(plan)
                    .description("Assinatura Plano " + plan.getName() + " - Org: " + sub.getOrganization().getName())
                    .quantity(1)
                    .unitAmountCents(plan.getPriceCents())
                    .amountCents(plan.getPriceCents())
                    .build();

            invoice.getItems().add(item);
            total += item.getAmountCents();
        }
        return total;
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
