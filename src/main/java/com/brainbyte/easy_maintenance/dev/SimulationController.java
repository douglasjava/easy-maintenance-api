package com.brainbyte.easy_maintenance.dev;

import com.brainbyte.easy_maintenance.affiliates.domain.ReferralCommission;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.AffiliateRepository;
import com.brainbyte.easy_maintenance.affiliates.infrastructure.persistence.ReferralCommissionRepository;
import com.brainbyte.easy_maintenance.billing.domain.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.*;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.*;
import com.brainbyte.easy_maintenance.infrastructure.saas.application.dto.AsaasDTO;
import com.brainbyte.easy_maintenance.leads.domain.LandingLead;
import com.brainbyte.easy_maintenance.leads.infrastructure.persistence.LandingLeadRepository;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.payment.domain.Payment;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.payment.infrastructure.persistence.PaymentRepository;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl.PaymentReceivedHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * DEV/STAGING ONLY — simulates the full affiliate referral flow without Asaas.
 * Never active in production (@Profile excludes it).
 */
@Slf4j
@RestController
@Profile({"dev", "staging"})
@RequestMapping("/easy-maintenance/api/v1/dev/simulate")
@RequiredArgsConstructor
@Tag(name = "Dev — Simulation", description = "Simulação de fluxos para QA (dev/staging apenas)")
public class SimulationController {

    private final AffiliateRepository affiliateRepository;
    private final ReferralCommissionRepository commissionRepository;
    private final LandingLeadRepository leadRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final BillingAccountRepository billingAccountRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final BillingPlanRepository billingPlanRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentReceivedHandler paymentReceivedHandler;
    private final PasswordEncoder passwordEncoder;

    public record SimulationRequest(
            String affiliateCode,
            String planCode,
            Integer amountCents,
            Integer stopAfterStep
    ) {}

    public record StepResult(int step, String name, String status, String detail) {}

    public record SimulationResult(
            String runId,
            List<StepResult> steps,
            Long userId,
            String orgCode,
            Long paymentId,
            Long commissionId,
            BigDecimal commissionAmount,
            String commissionStatus,
            String error
    ) {}

    @PostMapping("/affiliate-flow")
    @Transactional
    @Operation(
            summary = "Simular fluxo completo de indicação (8 steps)",
            description = """
                    Simula o fluxo end-to-end de um afiliado sem chamar o Asaas:
                    1) Valida afiliado · 2) Cria LandingLead · 3) Cria User+BillingAccount
                    4) Cria Org+UserOrg · 5) Cria Subscription+Item · 6) Cria Invoice
                    7) Cria Payment (PIX, cycleNumber=1) · 8) Dispara PAYMENT_RECEIVED → comissão

                    Use stopAfterStep para parar em qualquer step e inspecionar o estado.
                    """
    )
    public SimulationResult run(@RequestBody SimulationRequest req) {

        String runId = "sim-" + UUID.randomUUID().toString().substring(0, 8);
        List<StepResult> steps = new ArrayList<>();
        int stopAt = req.stopAfterStep() != null ? req.stopAfterStep() : 8;

        log.info("[SimFlow] runId={} affiliateCode={} planCode={} amountCents={} stopAt={}",
                runId, req.affiliateCode(), req.planCode(), req.amountCents(), stopAt);

        // --- Step 1: Validate affiliate ---
        var affiliateOpt = affiliateRepository.findByCode(req.affiliateCode());
        if (affiliateOpt.isEmpty()) {
            steps.add(step(1, "Validar afiliado", "FAIL", "Afiliado não encontrado: " + req.affiliateCode()));
            return result(runId, steps, null, null, null, null, null, null,
                    "Afiliado '" + req.affiliateCode() + "' não encontrado");
        }
        var affiliate = affiliateOpt.get();
        steps.add(step(1, "Validar afiliado", "OK",
                "Afiliado: " + affiliate.getName() + " (" + affiliate.getCode() + ")"));
        if (stopAt < 2) return result(runId, steps, null, null, null, null, null, null, null);

        // --- Step 2: Create LandingLead ---
        String simEmail = runId + "@sim.easymaintenance.local";
        LandingLead lead = LandingLead.builder()
                .email(simEmail)
                .name("Prospect Simulado " + runId)
                .affiliateCode(req.affiliateCode())
                .source("simulation")
                .status("NEW")
                .build();
        leadRepository.save(lead);
        steps.add(step(2, "Criar LandingLead", "OK", "email=" + simEmail + ", affiliateCode=" + req.affiliateCode()));
        if (stopAt < 3) return result(runId, steps, null, null, null, null, null, null, null);

        // --- Step 3: Create User + BillingAccount ---
        User user = User.builder()
                .email(simEmail)
                .name("Prospect Simulado " + runId)
                .role(Role.READER)
                .status(Status.ACTIVE)
                .passwordHash(passwordEncoder.encode("sim-password-" + runId))
                .referralCode(req.affiliateCode())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        user = userRepository.save(user);
        BillingAccount billingAccount = BillingAccount.builder()
                .user(user)
                .billingEmail(simEmail)
                .name("Prospect Simulado " + runId)
                .paymentMethod(PaymentMethodType.PIX)
                .build();
        billingAccount = billingAccountRepository.save(billingAccount);
        steps.add(step(3, "Criar User + BillingAccount", "OK",
                "userId=" + user.getId() + ", billingAccountId=" + billingAccount.getId() + ", referralCode=" + req.affiliateCode()));
        if (stopAt < 4) return result(runId, steps, user.getId(), null, null, null, null, null, null);

        // --- Step 4: Create Organization + UserOrganization ---
        String orgCode = "SIM-" + runId.toUpperCase().replace("-", "").substring(0, 8);
        Organization org = Organization.builder()
                .code(orgCode)
                .name("Org Simulada " + runId)
                .doc("00.000.000/0001-00")
                .companyType(com.brainbyte.easy_maintenance.ai.application.dto.CompanyType.OTHER)
                .referralCode(req.affiliateCode())
                .require2fa(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        org = organizationRepository.save(org);
        UserOrganization userOrg = UserOrganization.builder()
                .user(user)
                .organizationCode(orgCode)
                .build();
        userOrganizationRepository.save(userOrg);
        steps.add(step(4, "Criar Org + UserOrg", "OK",
                "orgCode=" + orgCode + ", referralCode=" + req.affiliateCode()));
        if (stopAt < 5) return result(runId, steps, user.getId(), orgCode, null, null, null, null, null);

        // --- Step 5: Resolve plan + Create Subscription + Item ---
        BillingPlan plan = resolvePlan(req.planCode());
        int planCents = req.amountCents() != null ? req.amountCents() : plan.getPriceCents();
        BillingSubscription subscription = BillingSubscription.builder()
                .billingAccount(billingAccount)
                .status(SubscriptionStatus.ACTIVE)
                .cycle(BillingCycle.MONTHLY)
                .externalSubscriptionId(null)   // null = PIX manual (no Asaas subscription link)
                .currentPeriodStart(Instant.now())
                .currentPeriodEnd(Instant.now().plusSeconds(30L * 24 * 3600))
                .totalCents((long) planCents)
                .cancelAtPeriodEnd(false)
                .build();
        subscription = billingSubscriptionRepository.save(subscription);
        BillingSubscriptionItem subItem = BillingSubscriptionItem.builder()
                .billingSubscription(subscription)
                .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION)
                .sourceId(orgCode)
                .plan(plan)
                .valueCents((long) planCents)
                .activatedAt(Instant.now())
                .build();
        billingSubscriptionItemRepository.save(subItem);
        steps.add(step(5, "Criar Subscription + Item", "OK",
                "subscriptionId=" + subscription.getId() + ", plan=" + plan.getCode() + ", valueCents=" + planCents));
        if (stopAt < 6) return result(runId, steps, user.getId(), orgCode, null, null, null, null, null);

        // --- Step 6: Create Invoice ---
        Invoice invoice = Invoice.builder()
                .payer(user)
                .currency("BRL")
                .periodStart(LocalDate.now())
                .periodEnd(LocalDate.now().plusMonths(1))
                .dueDate(LocalDate.now())
                .subtotalCents(planCents)
                .discountCents(0)
                .totalCents(planCents)
                .build();
        invoice = invoiceRepository.save(invoice);
        steps.add(step(6, "Criar Invoice", "OK",
                "invoiceId=" + invoice.getId() + ", totalCents=" + planCents));
        if (stopAt < 7) return result(runId, steps, user.getId(), orgCode, null, null, null, null, null);

        // --- Step 7: Create Payment (cycleNumber=1, PIX, PENDING) ---
        String extRef = "SIM-" + runId.toUpperCase() + "-CYCLE-1";
        String extPayId = "SIM-" + runId.toUpperCase();
        Payment payment = Payment.builder()
                .invoice(invoice)
                .billingSubscription(subscription)
                .payer(user)
                .cycleNumber(1)
                .provider(PaymentProvider.ASAAS)
                .methodType(PaymentMethodType.PIX)
                .status(PaymentStatus.PENDING)
                .amountCents(planCents)
                .currency("BRL")
                .externalPaymentId(extPayId)
                .externalReference(extRef)
                .build();
        payment = paymentRepository.save(payment);
        steps.add(step(7, "Criar Payment (cycleNumber=1, PIX, PENDING)", "OK",
                "paymentId=" + payment.getId() + ", externalReference=" + extRef + ", amountCents=" + planCents));
        if (stopAt < 8) return result(runId, steps, user.getId(), orgCode, payment.getId(), null, null, null, null);

        // --- Step 8: Fire PAYMENT_RECEIVED webhook → triggers commission ---
        AsaasDTO.PaymentObject paymentObj = new AsaasDTO.PaymentObject(
                extPayId, "sim-customer", null, "RECEIVED",
                BigDecimal.valueOf(planCents / 100.0),
                LocalDate.now(), LocalDate.now(),
                "Simulação " + runId, null, "PIX",
                extRef, null, null, null, null,
                BigDecimal.valueOf(planCents / 100.0),
                LocalDate.now(), LocalDate.now(),
                null, null, null, null);
        AsaasDTO.WebhookCheckoutEvent event = new AsaasDTO.WebhookCheckoutEvent(
                "sim-evt-" + runId, "PAYMENT_RECEIVED",
                java.time.LocalDateTime.now().toString(),
                null, null, paymentObj, null);

        paymentReceivedHandler.handle(event);

        // Verify commission was created
        final Long savedOrgId = org.getId();
        ReferralCommission commission = commissionRepository
                .findAllByAffiliateId(affiliate.getId())
                .stream()
                .filter(c -> c.getOrganizationId().equals(savedOrgId))
                .findFirst()
                .orElse(null);

        if (commission != null) {
            steps.add(step(8, "Disparar PAYMENT_RECEIVED → comissão gerada", "OK",
                    "commissionId=" + commission.getId()
                    + ", amount=R$" + commission.getCommissionAmount()
                    + ", status=" + commission.getStatus()));
            return result(runId, steps, user.getId(), orgCode, payment.getId(),
                    commission.getId(), commission.getCommissionAmount(),
                    commission.getStatus().name(), null);
        } else {
            steps.add(step(8, "Disparar PAYMENT_RECEIVED", "WARN",
                    "Webhook disparado mas comissão não encontrada — verifique logs do handler"));
            return result(runId, steps, user.getId(), orgCode, payment.getId(),
                    null, null, null, "Comissão não gerada — verifique se afiliado está ACTIVE e org tem referralCode");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private BillingPlan resolvePlan(String planCode) {
        if (planCode != null && !planCode.isBlank()) {
            return billingPlanRepository.findByCode(planCode)
                    .orElseThrow(() -> new IllegalArgumentException("Plano não encontrado: " + planCode));
        }
        return billingPlanRepository.findAll().stream()
                .filter(p -> p.getPriceCents() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum plano disponível no banco"));
    }

    private StepResult step(int n, String name, String status, String detail) {
        log.info("[SimFlow] Step {} [{}] {} — {}", n, status, name, detail);
        return new StepResult(n, name, status, detail);
    }

    private SimulationResult result(String runId, List<StepResult> steps,
                                    Long userId, String orgCode, Long paymentId,
                                    Long commissionId, BigDecimal commissionAmount,
                                    String commissionStatus, String error) {
        return new SimulationResult(runId, steps, userId, orgCode, paymentId,
                commissionId, commissionAmount, commissionStatus, error);
    }
}
