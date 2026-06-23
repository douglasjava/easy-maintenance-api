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
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.webhooks.asaas.strategy.impl.PaymentOverdueHandler;
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
@Profile({"dev", "staging", "debug"})
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
    private final PaymentOverdueHandler paymentOverdueHandler;
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

    // ═══════════════════════════════════════════════════════════════════════════
    // BILLING PAYMENT SIMULATION
    // ═══════════════════════════════════════════════════════════════════════════

    public record BillingSimulationRequest(
            /** PIX_FIRST | PIX_SECOND | CC_FIRST | CC_SECOND | PIX_OVERDUE */
            String scenario,
            String planCode,
            Integer amountCents,
            Integer stopAfterStep
    ) {}

    public record PaymentResult(int cycleNumber, String status, boolean cycleAdvanced, Long paymentId) {}

    public record BillingSimulationResult(
            String runId,
            String scenario,
            List<StepResult> steps,
            Long userId,
            Long subscriptionId,
            String subscriptionStatus,
            String currentPeriodEnd,
            List<PaymentResult> payments,
            String note,
            String error
    ) {}

    @PostMapping("/billing-payment")
    @Transactional
    @Operation(
            summary = "Simular fluxo de pagamento PIX / Cartão (dev/staging)",
            description = """
                    Simula os webhooks de pagamento sem chamar o Asaas.

                    Cenários disponíveis:
                    - PIX_FIRST: subscription PIX → PAYMENT_RECEIVED cycleNumber=1 → ciclo avançado
                    - PIX_SECOND: igual + segundo ciclo (verifica avanço repetido)
                    - CC_FIRST: subscription CC (externalSubscriptionId preenchido) → PAYMENT_RECEIVED → ciclo NÃO avançado localmente
                    - CC_SECOND: CC primeiro + segundo pagamento
                    - PIX_OVERDUE: PIX payment → PAYMENT_OVERDUE → payment/invoice OVERDUE
                      (subscription→PAST_DUE é feito por job separado, não simulado aqui)

                    stopAfterStep para inspecionar estado intermediário.
                    """
    )
    public BillingSimulationResult runBillingPayment(@RequestBody BillingSimulationRequest req) {
        String runId = "bsim-" + UUID.randomUUID().toString().substring(0, 8);
        List<StepResult> steps = new ArrayList<>();
        List<PaymentResult> payments = new ArrayList<>();
        int stopAt = req.stopAfterStep() != null ? req.stopAfterStep() : 99;

        String scenario = req.scenario() != null ? req.scenario().toUpperCase() : "PIX_FIRST";
        boolean isPix = !scenario.startsWith("CC");
        boolean isOverdue = scenario.equals("PIX_OVERDUE");
        boolean isSecond = scenario.contains("SECOND");

        log.info("[BillingSimFlow] runId={} scenario={}", runId, scenario);

        try {
            // ── Step 1: User + BillingAccount ──────────────────────────────
            String email = runId + "@bsim.easymaintenance.local";
            User user = User.builder()
                    .email(email).name("Billing Sim " + runId)
                    .role(Role.READER).status(Status.ACTIVE)
                    .passwordHash(passwordEncoder.encode("sim-" + runId))
                    .createdAt(Instant.now()).updatedAt(Instant.now()).build();
            user = userRepository.save(user);
            BillingAccount account = BillingAccount.builder()
                    .user(user).billingEmail(email).name("Billing Sim " + runId)
                    .paymentMethod(isPix ? PaymentMethodType.PIX : PaymentMethodType.CARD).build();
            account = billingAccountRepository.save(account);
            steps.add(step(1, "Criar User + BillingAccount", "OK",
                    "userId=" + user.getId() + ", method=" + (isPix ? "PIX" : "CARD")));
            if (stopAt < 2) return billingResult(runId, scenario, steps, user, null, null, payments, null, null);

            // ── Step 2: BillingSubscription ────────────────────────────────
            BillingPlan plan = resolvePlan(req.planCode());
            int planCents = req.amountCents() != null ? req.amountCents() : plan.getPriceCents();
            // PIX manual: externalSubscriptionId = null → shouldAdvanceCycle() returns true
            // CC: externalSubscriptionId filled → Asaas manages cycle, not advanced locally
            String extSubId = isPix ? null : "SIM-CC-SUB-" + runId.toUpperCase();
            BillingSubscription subscription = BillingSubscription.builder()
                    .billingAccount(account)
                    .status(SubscriptionStatus.TRIAL)
                    .cycle(BillingCycle.MONTHLY)
                    .externalSubscriptionId(extSubId)
                    .currentPeriodStart(Instant.now())
                    .currentPeriodEnd(Instant.now().plusSeconds(14L * 24 * 3600))
                    .totalCents((long) planCents)
                    .cancelAtPeriodEnd(false).build();
            subscription = billingSubscriptionRepository.save(subscription);
            steps.add(step(2, "Criar BillingSubscription", "OK",
                    "subscriptionId=" + subscription.getId()
                    + ", externalSubscriptionId=" + (extSubId != null ? extSubId : "null (PIX manual)")));
            if (stopAt < 3) return billingResult(runId, scenario, steps, user, subscription, null, payments, null, null);

            // ── Step 3: BillingSubscriptionItem ───────────────────────────
            BillingSubscriptionItem item = BillingSubscriptionItem.builder()
                    .billingSubscription(subscription)
                    .sourceType(BillingSubscriptionItemSourceType.ORGANIZATION)
                    .sourceId("BSIM-ORG-" + runId.toUpperCase())
                    .plan(plan).valueCents((long) planCents)
                    .activatedAt(Instant.now()).build();
            billingSubscriptionItemRepository.save(item);
            steps.add(step(3, "Criar BillingSubscriptionItem", "OK",
                    "plan=" + plan.getCode() + ", valueCents=" + planCents));
            if (stopAt < 4) return billingResult(runId, scenario, steps, user, subscription, null, payments, null, null);

            // ── Step 4: First Invoice + Payment ───────────────────────────
            Invoice inv1 = createInvoice(user, planCents);
            String extRef1 = "BSIM-" + runId.toUpperCase() + "-CYCLE-1";
            String extId1 = "BSIM-PAY1-" + runId.toUpperCase();
            Payment pay1 = createPayment(inv1, subscription, user, 1, isPix, planCents, extRef1, extId1);
            steps.add(step(4, "Criar Invoice + Payment cycleNumber=1", "OK",
                    "paymentId=" + pay1.getId() + ", method=" + (isPix ? "PIX" : "CARD") + ", status=PENDING"));
            if (stopAt < 5) return billingResult(runId, scenario, steps, user, subscription, null, payments, null, null);

            // ── Step 5: Fire event for payment 1 ──────────────────────────
            if (isOverdue) {
                paymentOverdueHandler.handle(buildEvent(extId1, extRef1, "PAYMENT_OVERDUE", isPix, planCents));
                subscription = billingSubscriptionRepository.findById(subscription.getId()).orElseThrow();
                pay1 = paymentRepository.findById(pay1.getId()).orElseThrow();
                steps.add(step(5, "Disparar PAYMENT_OVERDUE", "OK",
                        "payment.status=" + pay1.getStatus()
                        + ", invoice.status=" + pay1.getInvoice().getStatus()));
                payments.add(new PaymentResult(1, pay1.getStatus().name(), false, pay1.getId()));
                String note = "subscription.status permanece TRIAL/ACTIVE — " +
                        "transição para PAST_DUE ocorre via job SubscriptionBlockingJob (não simulado aqui)";
                return billingResult(runId, scenario, steps, user, subscription,
                        subscription.getCurrentPeriodEnd(), payments, note, null);
            }

            // PAYMENT_RECEIVED for cycle 1
            Instant periodEndBefore1 = subscription.getCurrentPeriodEnd();
            paymentReceivedHandler.handle(buildEvent(extId1, extRef1, "PAYMENT_RECEIVED", isPix, planCents));
            subscription = billingSubscriptionRepository.findById(subscription.getId()).orElseThrow();
            pay1 = paymentRepository.findById(pay1.getId()).orElseThrow();
            boolean cycle1Advanced = isPix && subscription.getCurrentPeriodEnd() != null
                    && !subscription.getCurrentPeriodEnd().equals(periodEndBefore1);
            steps.add(step(5, "Disparar PAYMENT_RECEIVED cycleNumber=1", "OK",
                    "payment.status=" + pay1.getStatus()
                    + ", subscription.status=" + subscription.getStatus()
                    + ", cycleAdvanced=" + cycle1Advanced));
            payments.add(new PaymentResult(1, pay1.getStatus().name(), cycle1Advanced, pay1.getId()));
            if (stopAt < 6 || !isSecond)
                return billingResult(runId, scenario, steps, user, subscription,
                        subscription.getCurrentPeriodEnd(), payments, buildNote(isPix), null);

            // ── Step 6+: Second payment (only for *_SECOND scenarios) ──────
            Invoice inv2 = createInvoice(user, planCents);
            String extRef2 = "BSIM-" + runId.toUpperCase() + "-CYCLE-2";
            String extId2 = "BSIM-PAY2-" + runId.toUpperCase();
            Payment pay2 = createPayment(inv2, subscription, user, 2, isPix, planCents, extRef2, extId2);
            steps.add(step(6, "Criar Invoice + Payment cycleNumber=2", "OK",
                    "paymentId=" + pay2.getId() + ", status=PENDING"));
            if (stopAt < 7) return billingResult(runId, scenario, steps, user, subscription,
                    subscription.getCurrentPeriodEnd(), payments, buildNote(isPix), null);

            Instant periodEndBefore2 = subscription.getCurrentPeriodEnd();
            paymentReceivedHandler.handle(buildEvent(extId2, extRef2, "PAYMENT_RECEIVED", isPix, planCents));
            subscription = billingSubscriptionRepository.findById(subscription.getId()).orElseThrow();
            pay2 = paymentRepository.findById(pay2.getId()).orElseThrow();
            boolean cycle2Advanced = isPix && subscription.getCurrentPeriodEnd() != null
                    && !subscription.getCurrentPeriodEnd().equals(periodEndBefore2);
            steps.add(step(7, "Disparar PAYMENT_RECEIVED cycleNumber=2", "OK",
                    "payment.status=" + pay2.getStatus()
                    + ", cycleAdvanced=" + cycle2Advanced));
            payments.add(new PaymentResult(2, pay2.getStatus().name(), cycle2Advanced, pay2.getId()));

            return billingResult(runId, scenario, steps, user, subscription,
                    subscription.getCurrentPeriodEnd(), payments, buildNote(isPix), null);

        } catch (Exception e) {
            log.error("[BillingSimFlow] Error in runId={}", runId, e);
            steps.add(step(steps.size() + 1, "ERRO", "FAIL", e.getMessage()));
            return billingResult(runId, scenario, steps, null, null, null, payments, null, e.getMessage());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Invoice createInvoice(User user, int totalCents) {
        return invoiceRepository.save(Invoice.builder()
                .payer(user).currency("BRL")
                .periodStart(LocalDate.now()).periodEnd(LocalDate.now().plusMonths(1))
                .dueDate(LocalDate.now())
                .subtotalCents(totalCents).discountCents(0).totalCents(totalCents).build());
    }

    private Payment createPayment(Invoice invoice, BillingSubscription subscription, User user,
                                   int cycleNumber, boolean isPix, int amountCents,
                                   String extRef, String extPayId) {
        return paymentRepository.save(Payment.builder()
                .invoice(invoice).billingSubscription(subscription).payer(user)
                .cycleNumber(cycleNumber)
                .provider(PaymentProvider.ASAAS)
                .methodType(isPix ? PaymentMethodType.PIX : PaymentMethodType.CARD)
                .status(PaymentStatus.PENDING)
                .amountCents(amountCents).currency("BRL")
                .externalPaymentId(extPayId).externalReference(extRef).build());
    }

    private AsaasDTO.WebhookCheckoutEvent buildEvent(String extPayId, String extRef,
                                                      String eventType, boolean isPix,
                                                      int amountCents) {
        AsaasDTO.PaymentObject paymentObj = new AsaasDTO.PaymentObject(
                extPayId, "sim-customer", null, "RECEIVED",
                BigDecimal.valueOf(amountCents / 100.0),
                LocalDate.now(), LocalDate.now(),
                "Simulação billing", null,
                isPix ? "PIX" : "CARD",
                extRef, null, null, null, null,
                BigDecimal.valueOf(amountCents / 100.0),
                LocalDate.now(), LocalDate.now(),
                null, null, null, null);
        return new AsaasDTO.WebhookCheckoutEvent(
                "bsim-evt-" + UUID.randomUUID().toString().substring(0, 8),
                eventType,
                java.time.LocalDateTime.now().toString(),
                null, null, paymentObj, null);
    }

    private BillingSimulationResult billingResult(String runId, String scenario,
                                                   List<StepResult> steps, User user,
                                                   BillingSubscription subscription,
                                                   Instant periodEnd,
                                                   List<PaymentResult> payments,
                                                   String note, String error) {
        return new BillingSimulationResult(
                runId, scenario, steps,
                user != null ? user.getId() : null,
                subscription != null ? subscription.getId() : null,
                subscription != null ? subscription.getStatus().name() : null,
                periodEnd != null ? periodEnd.toString() : null,
                payments, note, error);
    }

    private String buildNote(boolean isPix) {
        if (isPix) return "PIX manual: ciclo avançado localmente (externalSubscriptionId=null). " +
                "currentPeriodEnd atualizado em BillingSubscription.";
        return "Cartão: ciclo NÃO avançado localmente (externalSubscriptionId preenchido). " +
                "Asaas gerencia a renovação — handler apenas marca pagamento como RECEIVED.";
    }

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
