package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDetailResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceHistoryResponse;
import com.brainbyte.easy_maintenance.billing.application.dto.dashboard.DashboardResponseDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSummaryResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingDashboardService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/billing")
@Tag(name = "Billing Dashboard", description = "Endpoints de faturamento do usuário")
public class BillingController {

    private final BillingDashboardService dashboardService;
    private final InvoiceService invoiceService;
    private final AuthenticationService authenticationService;

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
