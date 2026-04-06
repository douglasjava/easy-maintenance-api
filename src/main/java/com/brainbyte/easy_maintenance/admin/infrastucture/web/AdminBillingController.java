package com.brainbyte.easy_maintenance.admin.infrastucture.web;

import com.brainbyte.easy_maintenance.billing.application.adapter.SubscriptionItemCancelAdapter;
import com.brainbyte.easy_maintenance.billing.application.adapter.SubscriptionItemChangePlanAdapter;
import com.brainbyte.easy_maintenance.billing.application.dto.*;
import com.brainbyte.easy_maintenance.billing.application.dto.request.SubscriptionItemChangePlanRequest;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemCancelResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.SubscriptionItemChangePlanResponse;
import com.brainbyte.easy_maintenance.billing.application.service.*;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.InvoiceStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.billing.mapper.IBillingMapper;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
    private final BillingAccountService accountService;
    private final InvoiceService invoiceService;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionService billingSubscriptionService;
    private final BillingSubscriptionItemService billingSubscriptionItemService;
    private final BillingSubscriptionItemService itemService;
    private final SubscriptionItemChangePlanAdapter changePlanAdapter;
    private final SubscriptionItemCancelAdapter cancelAdapter;
    private final UserRepository userRepository;
    private final BillingAccountService billingAccountService;

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

    @GetMapping("accounts")
    @Operation(summary = "Lista as contas de faturamento com filtros")
    public PageResponse<BillingAccountDTO.BillingAccountResponse> listBillingAccounts(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String doc,
            @RequestParam(required = false) BillingStatus status,
            @PageableDefault(size = 10) Pageable pageable) {
        return billingAccountService.findAll(email, name, doc, status, pageable);
    }

    @GetMapping("/users/{userId}/account")
    @Operation(summary = "Busca a conta de faturamento de um usuário")
    public BillingAccountDTO.BillingAccountResponse getAccount(@PathVariable Long userId) {
        
        return accountService.findByUserId(userId);
    }

    @PutMapping("/users/{userId}/account")
    @Operation(summary = "Atualiza ou cria a conta de faturamento de um usuário")
    public BillingAccountDTO.BillingAccountResponse updateAccount(@PathVariable Long userId,
                                                                  @Valid @RequestBody BillingAccountDTO.UpdateBillingAccountRequest request) {
        
        return accountService.updateOrCreate(userId, request);
    }

    @GetMapping("/overview")
    @Operation(summary = "Visão geral do faturamento para administradores")
    public BillingAdminDTO.BillingOverviewResponse getOverview(Pageable pageable) {
        var payers = accountService.getPayersOverview(pageable);
        var activeRevenue = billingSubscriptionRepository.sumActiveTotalCents();
        var totalOrganizations = billingSubscriptionRepository.count(); // Aproximação baseada em assinaturas

        var counters = new BillingAdminDTO.BillingCounters(
                totalOrganizations,
                payers.totalElements(),
                activeRevenue != null ? activeRevenue : 0L
        );

        return new BillingAdminDTO.BillingOverviewResponse(counters, payers);
    }

    @GetMapping("/invoices")
    @Operation(summary = "Lista e filtra faturas")
    public PageResponse<InvoiceDTO.InvoiceResponse> listInvoices(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateEnd,
            @RequestParam(required = false) Long payerUserId,
            Pageable pageable) {
        return invoiceService.listAllInvoices(status, periodStart, periodEnd, dueDateStart, dueDateEnd, payerUserId, pageable);
    }

    @GetMapping("/invoices/{invoiceId}")
    @Operation(summary = "Busca uma fatura por ID com seus itens")
    public InvoiceDTO.InvoiceResponse getInvoice(@PathVariable Long invoiceId) {
        return invoiceService.getInvoiceById(invoiceId);
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "Lista e filtra assinaturas (itens de assinatura)")
    public PageResponse<BillingAdminDTO.SubscriptionResponse> listSubscriptions(
            @RequestParam(required = false) String planCode,
            @RequestParam(required = false) String payerName,
            @RequestParam(required = false) SubscriptionStatus status,
            Pageable pageable) {
        return billingSubscriptionService.listSubscriptions(planCode, payerName, status, pageable);
    }

    @GetMapping("/subscription-item/{id}")
    @Operation(summary = "Buscar item da assinatura")
    public BillingSubscriptionResponse.SubscriptionItemResponse findByItemId(@PathVariable Long id) {
        return billingSubscriptionItemService.findById(id);
    }

    @GetMapping("/subscription-items/{id}")
    @Operation(summary = "Obter detalhes de um item da assinatura")
    public BillingSubscriptionResponse.SubscriptionItemResponse findById(@PathVariable Long id) {
        return itemService.findById(id);
    }

    @PostMapping("/subscription-items/{id}/change-plan")
    @Operation(summary = "Alterar o plano de um item da assinatura")
    public SubscriptionItemChangePlanResponse changePlan(
            @PathVariable Long id,
            @RequestHeader("X-id-User") Long idUser,
            @Valid @RequestBody SubscriptionItemChangePlanRequest request) {

        var user = getOrElseThrow(idUser);
        return changePlanAdapter.changePlan(id, user, request);
    }

    @PostMapping("/subscription-items/{id}/cancel")
    @Operation(summary = "Cancelar um item da assinatura")
    public SubscriptionItemCancelResponse cancel(@PathVariable Long id, @RequestHeader("X-id-User") Long idUser) {
        var user = getOrElseThrow(idUser);
        return cancelAdapter.cancel(id, user);
    }

    @PostMapping("/subscription-items/{id}/undo-cancel")
    @Operation(summary = "Desfazer o cancelamento de um item da assinatura")
    public SubscriptionItemCancelResponse undoCancel(@PathVariable Long id, @RequestHeader("X-id-User") Long idUser) {
        var user = getOrElseThrow(idUser);
        return cancelAdapter.undoCancel(id, user);
    }

    private User getOrElseThrow(Long idUser) {
        return userRepository.findById(idUser).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
    }

}
