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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodTransitionService {

    public static final String CARD_UPDATE_PREFIX = "CARD-UPDATE-";

    private final BillingAccountRepository billingAccountRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final AsaasClient asaasClient;
    private final AsaasProperties asaasProperties;

    // -----------------------------------------------------------------------
    // ACTIVE CC → PIX
    // -----------------------------------------------------------------------

    @Transactional
    public void transitionToPixFromCard(Long userId) {
        BillingAccount account = loadAccount(userId);
        BillingSubscription subscription = loadSubscription(userId);

        validateActiveStatus(subscription);

        if (account.getPaymentMethod() != PaymentMethodType.CARD) {
            throw new RuleException("Transição CC→PIX inválida: método atual não é Cartão de Crédito. Método atual: " + account.getPaymentMethod());
        }

        if (subscription.getExternalSubscriptionId() != null) {
            try {
                asaasClient.cancelSubscription(subscription.getExternalSubscriptionId());
                log.info("[PaymentTransition] Asaas subscription {} canceled for userId={}",
                        subscription.getExternalSubscriptionId(), userId);
            } catch (Exception e) {
                log.warn("[PaymentTransition] Could not cancel Asaas subscription {} (may already be gone): {}",
                        subscription.getExternalSubscriptionId(), e.getMessage());
            }
            subscription.setExternalSubscriptionId(null);
            billingSubscriptionRepository.save(subscription);
        }

        account.setPaymentMethod(PaymentMethodType.PIX);
        billingAccountRepository.save(account);

        log.info("[PaymentTransition] CC→PIX completed for userId={}", userId);
    }

    // -----------------------------------------------------------------------
    // ACTIVE CC → CC (card update — new checkout)
    // -----------------------------------------------------------------------

    @Transactional
    public BillingAccountDTO.CardUpdateResponse initiateCardUpdate(Long userId) {
        BillingAccount account = loadAccount(userId);
        BillingSubscription subscription = loadSubscription(userId);

        validateActiveStatus(subscription);

        if (account.getPaymentMethod() != PaymentMethodType.CARD) {
            throw new RuleException("Atualização de cartão só é permitida para assinaturas com método Cartão de Crédito. Método atual: " + account.getPaymentMethod());
        }

        LocalDate nextDueDate = subscription.getNextDueDate() != null
                ? subscription.getNextDueDate()
                : LocalDate.now().plusMonths(1);

        Invoice invoice = invoiceService
                .generateInvoiceForPayer(userId, nextDueDate, nextDueDate.plusMonths(1).minusDays(1))
                .orElseThrow(() -> new RuleException("Não foi possível gerar a fatura. Verifique se existem itens ativos na assinatura."));

        String externalReference = CARD_UPDATE_PREFIX + subscription.getId() + "-" + UUID.randomUUID();

        AsaasDTO.CreateCheckoutRequest req = buildCheckoutRequest(account, invoice, nextDueDate, subscription.getId(), externalReference);

        AsaasDTO.CheckoutResponse resp;
        try {
            resp = asaasClient.createCheckout(req);
        } catch (Exception e) {
            log.error("[PaymentTransition] Failed to create card update checkout for userId={}: {}", userId, e.getMessage());
            throw new AsaasException("Falha ao criar checkout de atualização de cartão. Tente novamente.", e);
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
                .externalReference(externalReference)
                .externalPaymentId(resp.id())
                .paymentLink(resp.link())
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[PaymentTransition] CC→CC card update checkout created: subscriptionId={}, userId={}, checkoutId={}, paymentId={}",
                subscription.getId(), userId, resp.id(), saved.getId());

        return new BillingAccountDTO.CardUpdateResponse(saved.getId(), resp.link());
    }

    // -----------------------------------------------------------------------
    // ACTIVE PIX → CC
    // -----------------------------------------------------------------------

    @Transactional
    public BillingAccountDTO.PaymentMethodTransitionResponse transitionToCardFromPix(Long userId) {
        BillingAccount account = loadAccount(userId);
        BillingSubscription subscription = loadSubscription(userId);

        validateActiveStatus(subscription);

        if (account.getPaymentMethod() != PaymentMethodType.PIX) {
            throw new RuleException("Transição PIX→CC inválida: método atual não é PIX. Método atual: " + account.getPaymentMethod());
        }

        account.setPaymentMethod(PaymentMethodType.CARD);
        billingAccountRepository.save(account);

        List<Payment> pendingPix = paymentRepository
                .findByBillingSubscriptionIdAndStatus(subscription.getId(), PaymentStatus.PENDING);

        if (!pendingPix.isEmpty()) {
            Payment pending = pendingPix.getFirst();
            Integer currentCycle = pending.getCycleNumber() != null ? pending.getCycleNumber() : 1;
            Integer effectiveCycle = currentCycle + 1;

            log.info("[PaymentTransition] PIX→CC: pending PIX exists (cycleNumber={}). CC effective from cycle {}. userId={}",
                    currentCycle, effectiveCycle, userId);

            return new BillingAccountDTO.PaymentMethodTransitionResponse(
                    "Método atualizado para Cartão de Crédito.",
                    "Existe um PIX pendente para o ciclo atual. O cartão de crédito será utilizado a partir do próximo ciclo (ciclo " + effectiveCycle + ").",
                    effectiveCycle
            );
        }

        log.info("[PaymentTransition] PIX→CC completed for userId={}", userId);

        return new BillingAccountDTO.PaymentMethodTransitionResponse(
                "Método atualizado para Cartão de Crédito. O cartão será utilizado no próximo ciclo de cobrança.",
                null,
                null
        );
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private BillingAccount loadAccount(Long userId) {
        return billingAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Billing account não encontrado para o usuário " + userId));
    }

    private BillingSubscription loadSubscription(Long userId) {
        return billingSubscriptionRepository.findByBillingAccountUserId(userId)
                .orElseThrow(() -> new NotFoundException("Assinatura não encontrada para o usuário " + userId));
    }

    private void validateActiveStatus(BillingSubscription subscription) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new RuleException("Alteração de método de pagamento para assinaturas ativas só é permitida quando a assinatura está em status ACTIVE. Status atual: " + subscription.getStatus());
        }
    }

    public AsaasDTO.CreateCheckoutRequest buildCheckoutRequest(BillingAccount account, Invoice invoice,
                                                        LocalDate nextDueDate, Long subscriptionId,
                                                        String externalReference) {
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

        var subscriptionPlan = new AsaasDTO.CheckoutSubscription(
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
                externalReference,
                callback,
                items,
                subscriptionPlan,
                account.getExternalCustomerId()
        );
    }
}
