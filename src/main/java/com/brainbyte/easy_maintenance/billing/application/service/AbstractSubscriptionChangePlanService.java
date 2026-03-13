package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscription;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.infrastructure.saas.client.AsaasClient;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractSubscriptionChangePlanService<T> {

    protected final BillingPlanRepository planRepository;
    protected final ProrataCalculator prorataCalculator;
    protected final InvoiceRepository invoiceRepository;
    protected final PaymentRepository paymentRepository;
    protected final AuditService auditService;
    protected final ObjectMapper objectMapper;
    protected final BillingSubscriptionRepository billingSubscriptionRepository;
    protected final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    protected final AsaasClient asaasClient;
    protected final BillingPlanFeaturesHelper featuresHelper;

    @Transactional
    public ChangePlanResponse changePlan(T sourceId, ChangePlanRequest request) {
        log.info("Processando mudança de plano para {}: novo plano {}", sourceId, request.newPlanCode());

        BillingSubscription billingSubscription = findBillingSubscription(sourceId);

        if (billingSubscription.getStatus() != SubscriptionStatus.ACTIVE && billingSubscription.getStatus() != SubscriptionStatus.TRIAL) {
            throw new RuleException("A assinatura precisa estar ATIVA ou em PERÍODO DE TESTE para alterar o plano.");
        }

        BillingSubscriptionItem item = findSubscriptionItem(billingSubscription, sourceId);

        BillingPlan currentPlan = item.getPlan();
        BillingPlan newPlan = planRepository.findByCode(request.newPlanCode())
                .orElseThrow(() -> new NotFoundException("Plano não encontrado: " + request.newPlanCode()));

        if (currentPlan.getCode().equals(newPlan.getCode())) {
            throw new RuleException("O novo plano é igual ao plano atual.");
        }

        if (request.applyImmediately() || newPlan.getPriceCents() > currentPlan.getPriceCents()) {
            return processUpgrade(billingSubscription, item, currentPlan, newPlan);
        } else {
            return processDowngrade(billingSubscription, item, newPlan, sourceId);
        }
    }

    protected abstract BillingSubscription findBillingSubscription(T sourceId);

    protected abstract BillingSubscriptionItem findSubscriptionItem(BillingSubscription subscription, T sourceId);

    protected abstract void validateDowngradeLimits(T sourceId, BillingPlan newPlan);

    protected ChangePlanResponse processUpgrade(BillingSubscription billingSubscription, BillingSubscriptionItem item, BillingPlan current, BillingPlan newPlan) {
        log.info("Processando UPGRADE para {}", item.getSourceId());

        int amountToChargeCents = prorataCalculator.calculateUpgradeCents(
                current.getPriceCents(),
                newPlan.getPriceCents(),
                billingSubscription.getCurrentPeriodStart(),
                billingSubscription.getCurrentPeriodEnd()
        );

        var user = billingSubscription.getBillingAccount().getUser();

        Invoice invoice = Invoice.builder()
                .payer(user)
                .periodStart(LocalDate.now())
                .periodEnd(billingSubscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate())
                .status(InvoiceStatus.OPEN)
                .dueDate(LocalDate.now().plusMonths(1))
                .subtotalCents(amountToChargeCents)
                .totalCents(amountToChargeCents)
                .build();

        InvoiceItem invoiceItem = InvoiceItem.builder()
                .invoice(invoice)
                .description("Upgrade de Plano: " + current.getName() + " -> " + newPlan.getName())
                .quantity(1)
                .unitAmountCents(amountToChargeCents)
                .amountCents(amountToChargeCents)
                .build();

        invoice.setItems(Collections.singletonList(invoiceItem));
        invoiceRepository.save(invoice);

        var checkout = createProrataCheckout(billingSubscription, invoice);

        Payment payment = Payment.builder()
                .externalReference(checkout.id())
                .invoice(invoice)
                .payer(user)
                .amountCents(amountToChargeCents)
                .status(PaymentStatus.PENDING)
                .provider(PaymentProvider.ASAAS)
                .build();

        paymentRepository.save(payment);

        setPendingPlan(billingSubscription, item, newPlan);
        billingSubscriptionRepository.save(billingSubscription);
        billingSubscriptionItemRepository.save(item);

        auditService.log("BillingSubscription", billingSubscription.getId().toString(), AuditAction.UPDATE, "Upgrade solicitado: " + current.getCode() + " -> " + newPlan.getCode());

        return new ChangePlanResponse(
                ChangePlanResponse.PlanChangeType.UPGRADE,
                invoice.getId(),
                amountToChargeCents,
                null
        );
    }

    protected abstract void setPendingPlan(BillingSubscription subscription, BillingSubscriptionItem item, BillingPlan newPlan);

    protected AsaasDTO.CheckoutResponse createProrataCheckout(BillingSubscription billingSubscription, Invoice invoice) {
        AsaasDTO.CheckoutItem checkoutItem = new AsaasDTO.CheckoutItem(
                null,
                "Pagamento Pró-rata - Upgrade de Assinatura",
                "Upgrade de Assinatura",
                1,
                BigDecimal.valueOf(invoice.getTotalCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
        );

        AsaasDTO.CreateCheckoutRequest checkoutRequest = new AsaasDTO.CreateCheckoutRequest(
                List.of(AsaasDTO.BillingType.PIX, AsaasDTO.BillingType.CREDIT_CARD, AsaasDTO.BillingType.BOLETO),
                null,
                null,
                null,
                null,
                List.of(checkoutItem),
                null,
                billingSubscription.getBillingAccount().getExternalCustomerId()
        );

        return asaasClient.createCheckout(checkoutRequest);
    }

    protected ChangePlanResponse processDowngrade(BillingSubscription billingSubscription, BillingSubscriptionItem item, BillingPlan newPlan, T sourceId) {
        log.info("Processando DOWNGRADE para {}", item.getSourceId());

        validateDowngradeLimits(sourceId, newPlan);

        setNextPlan(billingSubscription, item, newPlan);

        billingSubscriptionRepository.save(billingSubscription);
        billingSubscriptionItemRepository.save(item);

        auditService.log("BillingSubscription", billingSubscription.getId().toString(), AuditAction.UPDATE, "Downgrade agendado: " + item.getPlan().getCode() + " -> " + newPlan.getCode());

        return new ChangePlanResponse(
                ChangePlanResponse.PlanChangeType.DOWNGRADE,
                null,
                null,
                billingSubscription.getCurrentPeriodEnd().toString()
        );
    }

    protected abstract void setNextPlan(BillingSubscription subscription, BillingSubscriptionItem item, BillingPlan newPlan);
}
