package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.dashboard.DashboardResponseDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSummaryResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.PendingPaymentResponse;
import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingDashboardService {

    private final BillingAccountRepository billingAccountRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionItemRepository itemRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public BillingSummaryResponse getBillingSummary(Long userId) {
        log.info("Generating billing summary for user {}", userId);

        var subscriptionOpt = billingSubscriptionRepository.findByBillingAccountUserId(userId);
        var accountOpt = billingAccountRepository.findByUserId(userId);
        var recentInvoices = invoiceRepository.findRecentInvoices(userId, PageRequest.of(0, 5));

        if (subscriptionOpt.isEmpty()) {
            return BillingSummaryResponse.builder()
                    .items(Collections.emptyList())
                    .invoices(mapInvoicesToSummary(recentInvoices))
                    .billingAccount(mapAccountToSummary(accountOpt.orElse(null)))
                    .build();
        }

        var subscription = subscriptionOpt.get();
        var items = getBillingSubscriptionItemActive(subscription.getId());
        Map<String, String> orgNames = getOrganizationNames(items);

        return BillingSummaryResponse.builder()
                .subscription(mapToSubscriptionSummary(subscription))
                .items(items.stream()
                        .map(item -> mapToSubscriptionItemDTO(item, orgNames))
                        .toList())
                .invoices(mapInvoicesToSummary(recentInvoices))
                .billingAccount(mapAccountToSummary(accountOpt.orElse(null)))
                .build();
    }

    private static BillingSummaryResponse.SubscriptionItemDTO mapToSubscriptionItemDTO(BillingSubscriptionItem item, Map<String, String> orgNames) {
        return BillingSummaryResponse.SubscriptionItemDTO.builder()
                .id(item.getId())
                .type(item.getSourceType().name())
                .reference(item.getSourceId())
                .name(item.getSourceType() == BillingSubscriptionItemSourceType.USER ? "Plano Individual" : orgNames.getOrDefault(item.getSourceId(), item.getSourceId()))
                .valueCents(item.getValueCents())
                .plan(mapToPlanDTO(item.getPlan()))
                .pendingChange(item.getNextPlan() != null ? mapToPendingChangeDTO(item) : null)
                .build();
    }

    private static BillingSummaryResponse.PendingChangeDTO mapToPendingChangeDTO(BillingSubscriptionItem item) {
        return BillingSummaryResponse.PendingChangeDTO.builder()
                .nextPlan(mapToPlanDTO(item.getNextPlan()))
                .effectiveAt(item.getPlanChangeEffectiveAt())
                .build();
    }

    private static BillingSummaryResponse.PlanDTO mapToPlanDTO(BillingPlan item) {
        return BillingSummaryResponse.PlanDTO.builder()
                .code(item.getCode())
                .name(item.getName())
                .priceCents(item.getPriceCents().longValue())
                .build();
    }

    private static BillingSummaryResponse.SubscriptionSummaryDTO mapToSubscriptionSummary(BillingSubscription subscription) {
        return BillingSummaryResponse.SubscriptionSummaryDTO.builder()
                .id(subscription.getId())
                .status(subscription.getStatus().name())
                .cycle(subscription.getCycle().name())
                .totalCents(subscription.getTotalCents())
                .nextDueDate(subscription.getNextDueDate())
                .build();
    }

    private List<BillingSummaryResponse.InvoiceSummaryDTO> mapInvoicesToSummary(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) return Collections.emptyList();
        return invoices.stream()
                .map(this::mapInvoiceToSummary)
                .toList();
    }

    private BillingSummaryResponse.InvoiceSummaryDTO mapInvoiceToSummary(Invoice invoice) {
        if (invoice == null) return null;

        String paymentLink = null;
        if (invoice.getStatus() == InvoiceStatus.OPEN || invoice.getStatus() == InvoiceStatus.OVERDUE) {
            paymentLink = paymentRepository.findFirstByInvoiceIdAndStatusOrderByCreatedAtDesc(invoice.getId(), PaymentStatus.PENDING)
                    .map(Payment::getPaymentLink)
                    .orElse(null);
        }

        return BillingSummaryResponse.InvoiceSummaryDTO.builder()
                .id(invoice.getId())
                .status(invoice.getStatus().name())
                .amountCents(invoice.getTotalCents().longValue())
                .periodStart(invoice.getPeriodStart())
                .periodEnd(invoice.getPeriodEnd())
                .paymentLink(paymentLink)
                .fromPlanChange(false)
                .build();
    }

    private BillingSummaryResponse.BillingAccountSummaryDTO mapAccountToSummary(com.brainbyte.easy_maintenance.billing.domain.BillingAccount account) {
        if (account == null) return null;
        return BillingSummaryResponse.BillingAccountSummaryDTO.builder()
                .email(account.getBillingEmail())
                .paymentMethod(account.getPaymentMethod() != null ? account.getPaymentMethod().name() : null)
                // cardLast4 and cardBrand are not available in current BillingAccount entity, returning null as per current schema
                .cardLast4(null)
                .cardBrand(null)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardResponseDTO getDashboard(Long userId) {

        log.info("Gerando dados para dashboard de faturamento para usuário {}", userId);

        var account = billingAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Billing account not found for user: " + userId));

        var subscription = billingSubscriptionRepository.findByBillingAccountUserId(userId).orElse(null);

        List<BillingSubscriptionItem> items = Collections.emptyList();
        if (subscription != null) {
            items = getBillingSubscriptionItemActive(subscription.getId());
        }

        Map<String, String> orgNames = getOrganizationNames(items);

        var nextInvoice = invoiceRepository.findFirstByPayerIdAndStatusOrderByCreatedAtDesc(userId, InvoiceStatus.OPEN).orElse(null);

        var recentPayments = paymentRepository.findByPayerIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 5));

        return DashboardResponseDTO.builder()
                .account(buildAccountDTO(account))
                .summary(buildSummaryDTO(subscription))
                .paymentMethod(buildPaymentMethodDTO(account))
                .subscriptions(buildSubscriptionsDTO(items, subscription, orgNames))
                .nextInvoice(buildNextInvoiceDTO(nextInvoice))
                .recentPayments(buildRecentPaymentsDTO(recentPayments))
                .build();

    }

    private DashboardResponseDTO.DashboardAccountDTO buildAccountDTO(com.brainbyte.easy_maintenance.billing.domain.BillingAccount account) {
        return DashboardResponseDTO.DashboardAccountDTO.builder()
                .status(account.getStatus().name())
                .billingEmail(account.getBillingEmail())
                .name(account.getName())
                .document(account.getDoc())
                .build();
    }

    private DashboardResponseDTO.DashboardSummaryDTO buildSummaryDTO(com.brainbyte.easy_maintenance.billing.domain.BillingSubscription subscription) {
        return DashboardResponseDTO.DashboardSummaryDTO.builder()
                .totalMonthlyCents(subscription != null ? subscription.getTotalCents() : 0L)
                .currency("BRL")
                .nextDueDate(subscription != null ? subscription.getNextDueDate() : null)
                .build();
    }

    private DashboardResponseDTO.DashboardPaymentMethodDTO buildPaymentMethodDTO(com.brainbyte.easy_maintenance.billing.domain.BillingAccount account) {
        return DashboardResponseDTO.DashboardPaymentMethodDTO.builder()
                .type(account.getPaymentMethod() != null ? account.getPaymentMethod().name() : null)
                .build();
    }

    private List<DashboardResponseDTO.DashboardSubscriptionDTO> buildSubscriptionsDTO(
            List<BillingSubscriptionItem> items, 
            com.brainbyte.easy_maintenance.billing.domain.BillingSubscription subscription, 
            Map<String, String> orgNames) {
        return items.stream()
                .map(item -> DashboardResponseDTO.DashboardSubscriptionDTO.builder()
                        .type(item.getSourceType().name())
                        .sourceId(item.getSourceId())
                        .name(item.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION 
                                ? orgNames.getOrDefault(item.getSourceId(), "Organization " + item.getSourceId())
                                : "Personal")
                        .planCode(item.getPlan().getCode())
                        .planName(item.getPlan().getName())
                        .valueCents(item.getValueCents())
                        .status(subscription != null ? subscription.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private DashboardResponseDTO.DashboardInvoiceDTO buildNextInvoiceDTO(com.brainbyte.easy_maintenance.billing.domain.Invoice nextInvoice) {
        if (nextInvoice == null) {
            return null;
        }
        return DashboardResponseDTO.DashboardInvoiceDTO.builder()
                .invoiceId(nextInvoice.getId())
                .dueDate(nextInvoice.getDueDate())
                .totalCents(nextInvoice.getTotalCents())
                .status(nextInvoice.getStatus().name())
                .build();
    }

    private List<DashboardResponseDTO.DashboardRecentPaymentDTO> buildRecentPaymentsDTO(List<com.brainbyte.easy_maintenance.payment.domain.Payment> recentPayments) {
        return recentPayments.stream()
                .map(payment -> DashboardResponseDTO.DashboardRecentPaymentDTO.builder()
                        .invoiceId(payment.getInvoice().getId())
                        .amountCents(payment.getAmountCents())
                        .status(payment.getStatus().name())
                        .paidAt(payment.getPaidAt() != null ? java.time.LocalDate.ofInstant(payment.getPaidAt(), java.time.ZoneId.systemDefault()) : null)
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, String> getOrganizationNames(List<BillingSubscriptionItem> items) {
        List<String> orgCodes = items.stream()
                .filter(i -> i.getSourceType() == BillingSubscriptionItemSourceType.ORGANIZATION)
                .map(BillingSubscriptionItem::getSourceId)
                .collect(Collectors.toList());

        if (orgCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        return organizationRepository.findAllByCodeIn(orgCodes).stream()
                .collect(Collectors.toMap(Organization::getCode, Organization::getName));
    }

    /**
     * Returns the most recent PENDING payment for the user's active subscription, or null if none.
     *
     * <p>Used by the billing page to display a pending PIX QR Code (or any other pending method).
     * Returns null (not 404) when there is no pending payment — this is a normal state for
     * credit card users whose payments are charged automatically.
     */
    @Transactional(readOnly = true)
    public PendingPaymentResponse getPendingPayment(Long userId) {
        var subscriptionOpt = billingSubscriptionRepository.findByBillingAccountUserId(userId);
        if (subscriptionOpt.isEmpty()) {
            return null;
        }

        var subscription = subscriptionOpt.get();
        var paymentOpt = paymentRepository
                .findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                        subscription.getId(), PaymentStatus.PENDING);

        if (paymentOpt.isEmpty()) {
            // Also check OVERDUE — user may have missed the payment window
            paymentOpt = paymentRepository
                    .findFirstByBillingSubscriptionIdAndStatusOrderByCreatedAtDesc(
                            subscription.getId(), PaymentStatus.OVERDUE);
        }

        return paymentOpt.map(payment -> PendingPaymentResponse.builder()
                .paymentId(payment.getId())
                .methodType(payment.getMethodType())
                .status(payment.getStatus())
                .amountCents(payment.getAmountCents())
                .currency(payment.getCurrency())
                .paymentLink(payment.getPaymentLink())
                .pixQrCode(payment.getPixQrCode())
                .pixQrCodeBase64(payment.getPixQrCodeBase64())
                .pixExpiresAt(payment.getPixExpiresAt())
                .build()
        ).orElse(null);
    }

    private List<BillingSubscriptionItem> getBillingSubscriptionItemActive(Long idSubscription) {

        return itemRepository.findAllByBillingSubscriptionIdFetchPlan(idSubscription).stream()
                .filter(item -> item.getCanceledAt() == null)
                .toList();
    }

}
