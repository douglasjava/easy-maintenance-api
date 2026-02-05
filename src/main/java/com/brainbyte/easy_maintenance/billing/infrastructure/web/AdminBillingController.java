package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.*;
import com.brainbyte.easy_maintenance.billing.application.service.BillingAccountService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.SubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/private/admin/billing")
@Tag(name = "Billing Admin", description = "Operações administrativas de faturamento")
public class AdminBillingController {

    private final BillingPlanService planService;
    private final SubscriptionService subscriptionService;
    private final BillingAccountService accountService;
    private final InvoiceService invoiceService;

    @GetMapping("/plans")
    @Operation(summary = "Lista todos os planos de faturamento")
    public List<BillingPlanDTO.BillingPlanResponse> listPlans() {
        
        return planService.listAll();
    }

    @PostMapping("/plans")
    @Operation(summary = "Cria um novo plano de faturamento")
    public BillingPlanDTO.BillingPlanResponse createPlan(@Valid @RequestBody BillingPlanDTO.CreateBillingPlanRequest request) {
        
        return planService.create(request);
    }

    @PatchMapping("/plans/{code}")
    @Operation(summary = "Atualiza um plano de faturamento existente")
    public BillingPlanDTO.BillingPlanResponse updatePlan(@PathVariable String code, @RequestBody BillingPlanDTO.UpdateBillingPlanRequest request) {
        
        return planService.update(code, request);
    }

    @GetMapping("/organizations/{orgCode}/subscription")
    @Operation(summary = "Busca a assinatura de uma organização")
    public OrganizationSubscriptionDTO.SubscriptionResponse getSubscription(@PathVariable String orgCode) {
        
        return subscriptionService.findByOrganizationCode(orgCode);
    }

    @PutMapping("/organizations/{orgCode}/subscription")
    @Operation(summary = "Atualiza ou cria a assinatura de uma organização")
    public OrganizationSubscriptionDTO.SubscriptionResponse updateSubscription(@PathVariable String orgCode, @Valid @RequestBody OrganizationSubscriptionDTO.UpdateSubscriptionRequest request) {
        
        return subscriptionService.updateOrCreate(orgCode, request);
    }

    @GetMapping("/users/{userId}/account")
    @Operation(summary = "Busca a conta de faturamento de um usuário")
    public BillingAccountDTO.BillingAccountResponse getAccount(@PathVariable Long userId) {
        
        return accountService.findByUserId(userId);
    }

    @PutMapping("/users/{userId}/account")
    @Operation(summary = "Atualiza ou cria a conta de faturamento de um usuário")
    public BillingAccountDTO.BillingAccountResponse updateAccount(@PathVariable Long userId, @Valid @RequestBody BillingAccountDTO.UpdateBillingAccountRequest request) {
        
        return accountService.updateOrCreate(userId, request);
    }

    @PostMapping("/invoices/generate")
    @Operation(summary = "Gera faturas para um determinado período")
    public void generateInvoices(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        
        invoiceService.generateInvoices(periodStart, periodEnd);
    }

    @GetMapping("/overview")
    @Operation(summary = "Visão geral do faturamento para administradores")
    public BillingAdminDTO.BillingOverviewResponse getOverview() {
        var counters = subscriptionService.getCounters();
        var topPayers = accountService.getTopPayers();
        var recentSubscriptions = subscriptionService.listSubscriptions(null, null, null, null);
        
        return new BillingAdminDTO.BillingOverviewResponse(counters, topPayers, recentSubscriptions);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Lista e filtra assinaturas")
    public List<OrganizationSubscriptionDTO.SubscriptionResponse> listSubscriptions(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String planCode,
            @RequestParam(required = false) Long payerUserId,
            @RequestParam(required = false) String queryNameOrCodeOrganization) {
        return subscriptionService.listSubscriptions(status, planCode, payerUserId, queryNameOrCodeOrganization);
    }

    @GetMapping("/invoices")
    @Operation(summary = "Lista e filtra faturas")
    public PageResponse<InvoiceDTO.InvoiceResponse> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false) Long payerUserId,
            Pageable pageable) {
        return invoiceService.listAllInvoices(status, periodStart, periodEnd, payerUserId, pageable);
    }

    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Busca uma fatura por ID com seus itens")
    public InvoiceDTO.InvoiceResponse getInvoice(@PathVariable Long invoiceId) {
        return invoiceService.getInvoiceById(invoiceId);
    }

}
