package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.domain.BillingAccount;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingAccountRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.AsaasException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.infrastructure.saas.properties.AsaasProperties;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingRecoveryService {

    private final BillingAccountRepository billingAccountRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final AsaasClient asaasClient;
    private final AsaasProperties asaasProperties;

    @Transactional
    public BillingAccountDTO.RecoveryPixResponse recoverWithPix(Long userId) {
        BillingAccount account = loadAccount(userId);
        BillingSubscription subscription = loadSubscription(userId);
        validatePastDue(subscription);

        List<Payment> pending = paymentRepository
                .findByBillingSubscriptionIdAndStatus(subscription.getId(), PaymentStatus.PENDING);
        if (!pending.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Já existe um pagamento pendente para esta assinatura. Aguarde o vencimento ou utilize o link de pagamento existente.");
        }

        LocalDate today = LocalDate.now();
        Invoice invoice = generateRecoveryInvoice(userId, today);

        String externalReference = "RECOVERY-" + subscription.getId() + "-" + UUID.randomUUID();
        AsaasDTO.CreatePaymentRequest req = new AsaasDTO.CreatePaymentRequest(
                account.getExternalCustomerId(),
                AsaasDTO.BillingType.PIX,
                BigDecimal.valueOf(invoice.getTotalCents(), 2),
                today,
                "Recuperação de assinatura - Easy Maintenance",
                externalReference
        );

        AsaasDTO.PaymentResponse asaasResp;
        try {
            asaasResp = asaasClient.createPayment(req);
        } catch (Exception e) {
            log.error("[BillingRecovery] Failed to create PIX recovery payment for userId={}: {}", userId, e.getMessage());
            throw new AsaasException("Falha ao criar cobrança PIX no gateway. Tente novamente.", e);
        }

        String pixQrCode = null;
        String pixQrCodeBase64 = null;
        LocalDateTime pixExpiresAt = null;

        try {
            AsaasDTO.PixQrCode qr = asaasClient.getPixQrCode(asaasResp.id());
            if (qr != null) {
                pixQrCode = qr.payload();
                pixQrCodeBase64 = qr.encodedImage();
                pixExpiresAt = qr.expirationDate();
            }
        } catch (Exception e) {
            log.warn("[BillingRecovery] Could not fetch PIX QR code for paymentId={}: {}", asaasResp.id(), e.getMessage());
        }

        Integer cycleNumber = paymentRepository.findMaxCycleNumberByBillingSubscriptionId(subscription.getId()) + 1;

        Payment payment = Payment.builder()
                .invoice(invoice)
                .billingSubscription(subscription)
                .cycleNumber(cycleNumber)
                .payer(account.getUser())
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .externalReference(externalReference)
                .externalPaymentId(asaasResp.id())
                .paymentLink(asaasResp.invoiceUrl())
                .pixQrCode(pixQrCode)
                .pixQrCodeBase64(pixQrCodeBase64)
                .pixExpiresAt(pixExpiresAt != null ? pixExpiresAt.toInstant(ZoneOffset.UTC) : null)
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[BillingRecovery] PIX recovery charge created: subscriptionId={}, userId={}, asaasPaymentId={}, paymentId={}",
                subscription.getId(), userId, asaasResp.id(), saved.getId());

        return new BillingAccountDTO.RecoveryPixResponse(
                saved.getId(),
                saved.getPaymentLink(),
                pixQrCode,
                pixQrCodeBase64,
                pixExpiresAt
        );
    }

    @Transactional
    public BillingAccountDTO.RecoveryCheckoutResponse recoverWithCheckout(Long userId) {
        BillingAccount account = loadAccount(userId);
        BillingSubscription subscription = loadSubscription(userId);
        validatePastDue(subscription);

        // Cancel old Asaas subscription if it still exists
        if (subscription.getExternalSubscriptionId() != null) {
            try {
                asaasClient.cancelSubscription(subscription.getExternalSubscriptionId());
                log.info("[BillingRecovery] Asaas subscription {} canceled for userId={}", subscription.getExternalSubscriptionId(), userId);
            } catch (Exception e) {
                log.warn("[BillingRecovery] Could not cancel Asaas subscription {} (may already be gone): {}",
                        subscription.getExternalSubscriptionId(), e.getMessage());
            }
            subscription.setExternalSubscriptionId(null);
            billingSubscriptionRepository.save(subscription);
        }

        LocalDate today = LocalDate.now();
        Invoice invoice = generateRecoveryInvoice(userId, today);

        AsaasDTO.CreateCheckoutRequest checkoutReq = buildCheckoutRequest(account, invoice, today, subscription.getId());

        AsaasDTO.CheckoutResponse checkoutResp;
        try {
            checkoutResp = asaasClient.createCheckout(checkoutReq);
        } catch (Exception e) {
            log.error("[BillingRecovery] Failed to create CC checkout for userId={}: {}", userId, e.getMessage());
            throw new AsaasException("Falha ao criar checkout no gateway. Tente novamente.", e);
        }

        Payment payment = Payment.builder()
                .invoice(invoice)
                .billingSubscription(subscription)
                .payer(account.getUser())
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(invoice.getTotalCents())
                .currency(invoice.getCurrency())
                .externalReference("RECOVERY-CC-" + subscription.getId() + "-" + UUID.randomUUID())
                .externalPaymentId(checkoutResp.id())
                .paymentLink(checkoutResp.link())
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[BillingRecovery] CC checkout recovery created: subscriptionId={}, userId={}, checkoutId={}, paymentId={}",
                subscription.getId(), userId, checkoutResp.id(), saved.getId());

        return new BillingAccountDTO.RecoveryCheckoutResponse(saved.getId(), checkoutResp.link());
    }

    private BillingAccount loadAccount(Long userId) {
        return billingAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Billing account não encontrado para o usuário " + userId));
    }

    private BillingSubscription loadSubscription(Long userId) {
        return billingSubscriptionRepository.findByBillingAccountUserId(userId)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para o usuário " + userId));
    }

    private void validatePastDue(BillingSubscription subscription) {
        if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
            throw new RuleException("Recuperação de pagamento só é permitida quando a assinatura está em atraso (PAST_DUE). Status atual: " + subscription.getStatus());
        }
    }

    private Invoice generateRecoveryInvoice(Long userId, LocalDate today) {
        LocalDate periodEnd = today.plusMonths(1).minusDays(1);
        return invoiceService.generateInvoiceForPayer(userId, today, periodEnd)
                .orElseThrow(() -> new RuleException("Não foi possível gerar a fatura para recuperação. Verifique se existem itens ativos na assinatura."));
    }

    private AsaasDTO.CreateCheckoutRequest buildCheckoutRequest(BillingAccount account, Invoice invoice,
                                                                LocalDate nextDueDate, Long subscriptionId) {
        BillingCycle billingCycle = invoice.getItems().isEmpty()
                ? BillingCycle.MONTHLY
                : invoice.getItems().getFirst().getPlan().getBillingCycle();

        AsaasDTO.Cycle cycle = billingCycle == BillingCycle.YEARLY ? AsaasDTO.Cycle.YEARLY : AsaasDTO.Cycle.MONTHLY;

        var items = invoice.getItems().stream()
                .map(invoiceItem -> new AsaasDTO.CheckoutItem(
                        "INVITEM-" + invoiceItem.getId() + "-" + UUID.randomUUID(),
                        invoiceItem.getDescription(),
                        invoiceItem.getPlan().getName(),
                        invoiceItem.getQuantity(),
                        BigDecimal.valueOf(invoiceItem.getAmountCents(), 2)
                ))
                .toList();

        var subscription = new AsaasDTO.CheckoutSubscription(
                cycle,
                nextDueDate.toString(),
                nextDueDate.toString()
        );

        var callback = new AsaasDTO.CheckoutCallback(
                asaasProperties.checkoutSuccessUrl(),
                asaasProperties.checkoutCancelUrl(),
                asaasProperties.checkoutExpiredUrl()
        );

        return new AsaasDTO.CreateCheckoutRequest(
                List.of(AsaasDTO.BillingType.CREDIT_CARD),
                List.of(AsaasDTO.ChargeTypes.RECURRENT),
                asaasProperties.checkoutMinutesToExpire(),
                "RECOVERY-CC-" + subscriptionId,
                callback,
                items,
                subscription,
                account.getExternalCustomerId()
        );
    }
}
