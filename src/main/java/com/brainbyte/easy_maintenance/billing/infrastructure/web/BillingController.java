package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.BillingPlanDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.InvoiceDetailResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.response.InvoiceHistoryResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.dashboard.DashboardResponseDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSummaryResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingAccountService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingDashboardService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/billing")
@Tag(name = "Billing Dashboard", description = "Endpoints de faturamento do usuário")
public class BillingController {

    private final BillingDashboardService dashboardService;
    private final InvoiceService invoiceService;
    private final AuthenticationService authenticationService;
    private final BillingAccountService billingAccountService;
    private final BillingPlanService planService;

    @GetMapping("/plans")
    @Operation(summary = "Lista planos disponíveis com features para exibição pública")
    public List<BillingPlanDTO.PublicPlanResponse> listPublicPlans() {
        return planService.listPublicPlans();
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

    @GetMapping("/dashboard")
    @Operation(summary = "Retorna as informações consolidadas do dashboard de faturamento")
    public DashboardResponseDTO getDashboard() {
        var user = authenticationService.getCurrentUser();
        return dashboardService.getDashboard(user.getId());
    }

    @GetMapping("/summary")
    @Operation(summary = "Retorna o resumo completo de faturamento do usuário")
    public BillingSummaryResponse getSummary() {
        var user = authenticationService.getCurrentUser();
        return dashboardService.getBillingSummary(user.getId());
    }

    @GetMapping("/invoices")
    @Operation(summary = "Retorna o histórico de faturas do usuário autenticado")
    public PageResponse<InvoiceHistoryResponse> getInvoiceHistory(
            @PageableDefault(size = 10) Pageable pageable) {
        var user = authenticationService.getCurrentUser();
        return invoiceService.getInvoiceHistory(user.getId(), pageable);
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Retorna os detalhes de uma fatura específica")
    public InvoiceDetailResponse getInvoiceDetail(@PathVariable Long id) {
        var user = authenticationService.getCurrentUser();
        return invoiceService.getInvoiceDetail(user.getId(), id);
    }

}
