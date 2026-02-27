package com.brainbyte.easy_maintenance.billing.application.service;

import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.ChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.domain.BillingPlan;
import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.billing.domain.InvoiceItem;
import com.brainbyte.easy_maintenance.billing.domain.UserSubscription;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingPlanRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.InvoiceRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.UserSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditAction;
import com.brainbyte.easy_maintenance.infrastructure.audit.AuditService;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPlanChangeService {

    private final UserSubscriptionRepository repository;
    private final BillingPlanRepository planRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final ProrataCalculator prorataCalculator;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChangePlanResponse changePlan(Long userId, ChangePlanRequest request) {
        log.info("Processando mudança de plano para usuário {}: novo plano {}", userId, request.newPlanCode());

        UserSubscription subscription = repository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Subscription not found for user: " + userId));

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE && subscription.getStatus() != SubscriptionStatus.TRIAL) {
            throw new RuleException("Subscription must be ACTIVE or TRIAL to change plan");
        }

        BillingPlan currentPlan = subscription.getPlan();
        BillingPlan newPlan = planRepository.findByCode(request.newPlanCode())
                .orElseThrow(() -> new NotFoundException("Plan not found: " + request.newPlanCode()));

        if (currentPlan.getCode().equals(newPlan.getCode())) {
            throw new RuleException("New plan is the same as the current plan");
        }

        if (newPlan.getPriceCents() > currentPlan.getPriceCents()) {
            return processUpgrade(subscription, currentPlan, newPlan);
        } else {
            return processDowngrade(subscription, currentPlan, newPlan);
        }
    }

    private ChangePlanResponse processUpgrade(UserSubscription subscription, BillingPlan current, BillingPlan newPlan) {
        log.info("Processando UPGRADE para usuário {}", subscription.getUser().getId());

        BigDecimal amountToCharge = prorataCalculator.calculateUpgradeAmount(
                current.getPriceCents(),
                newPlan.getPriceCents(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd()
        );

        Invoice invoice = Invoice.builder()
                .payer(subscription.getUser())
                .periodStart(LocalDate.now())
                .periodEnd(subscription.getCurrentPeriodEnd().atZone(ZoneOffset.UTC).toLocalDate())
                .status(InvoiceStatus.OPEN)
                .dueDate(LocalDate.now())
                .subtotalCents(amountToCharge.multiply(BigDecimal.valueOf(100)).intValue())
                .totalCents(amountToCharge.multiply(BigDecimal.valueOf(100)).intValue())
                .build();

        InvoiceItem item = InvoiceItem.builder()
                .invoice(invoice)
                .plan(newPlan)
                .description("Upgrade de plano (pró-rata): " + current.getName() + " -> " + newPlan.getName())
                .quantity(1)
                .unitAmountCents(invoice.getTotalCents())
                .amountCents(invoice.getTotalCents())
                .build();
        
        invoice.setItems(Collections.singletonList(item));
        var savedInvoice = invoiceRepository.save(invoice);

        Payment payment = Payment.builder()
                .invoice(savedInvoice)
                .payer(subscription.getUser())
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(savedInvoice.getTotalCents())
                .externalReference("UPGRADE-" + subscription.getId() + "-" + System.currentTimeMillis())
                .build();
        
        paymentRepository.save(payment);

        subscription.setPlan(newPlan);
        subscription.setNextPlan(null);
        subscription.setPlanChangeEffectiveAt(null);
        repository.save(subscription);

        auditService.log("USER_SUBSCRIPTION", subscription.getId().toString(), AuditAction.UPDATE, "UPGRADE to " + newPlan.getCode());

        return new ChangePlanResponse(
                ChangePlanResponse.PlanChangeType.UPGRADE,
                savedInvoice.getId(),
                amountToCharge,
                "IMMEDIATE"
        );
    }

    private ChangePlanResponse processDowngrade(UserSubscription subscription, BillingPlan current, BillingPlan newPlan) {
        log.info("Processando DOWNGRADE para usuário {}", subscription.getUser().getId());

        validateDowngradeLimits(subscription.getUser().getId(), newPlan);

        subscription.setNextPlan(newPlan);
        subscription.setPlanChangeEffectiveAt(subscription.getCurrentPeriodEnd());
        repository.save(subscription);

        auditService.log("USER_SUBSCRIPTION", subscription.getId().toString(), AuditAction.UPDATE, "DOWNGRADE scheduled to " + newPlan.getCode());

        return new ChangePlanResponse(
                ChangePlanResponse.PlanChangeType.DOWNGRADE,
                null,
                BigDecimal.ZERO,
                "NEXT_CYCLE"
        );
    }

    private void validateDowngradeLimits(Long userId, BillingPlan newPlan) {
        try {
            JsonNode features = objectMapper.readTree(newPlan.getFeaturesJson());
            int maxOrgs = features.has("maxOrganizations") ? features.get("maxOrganizations").asInt() : Integer.MAX_VALUE;

            long currentOrgs = userOrganizationRepository.countByUserId(userId);

            if (currentOrgs > maxOrgs) {
                throw new RuleException("Plano não suporta a quantidade atual de organizações (" + currentOrgs + " > " + maxOrgs + ")");
            }
        } catch (JsonProcessingException e) {
            log.error("Erro ao processar features_json do plano {}", newPlan.getCode(), e);
            throw new RuleException("Erro ao validar limites do plano");
        }
    }
}
