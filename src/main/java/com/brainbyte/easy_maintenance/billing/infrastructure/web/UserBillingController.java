package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.BillingAccountDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.response.MySubscriptionStatusResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingAccountService;
import com.brainbyte.easy_maintenance.billing.application.service.BillingQueryService;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing")
@Tag(name = "Billing User", description = "Operações de faturamento para o usuário autenticado")
public class UserBillingController {

    private final InvoiceService invoiceService;
    private final BillingAccountService accountService;
    private final BillingQueryService billingQueryService;
    private final AuthenticationService authenticationService;

    @GetMapping("/summary")
    @Operation(summary = "Retorna o resumo de faturamento do usuário logado")
    public InvoiceDTO.BillingSummaryResponse getSummary() {
        Long userId = authenticationService.getCurrentUser().getId();
        return invoiceService.getSummary(userId);
    }

    @GetMapping("/invoices")
    @PageableAsQueryParam
    @Operation(summary = "Lista as faturas do usuário logado de forma paginada")
    public PageResponse<InvoiceDTO.InvoiceResponse> listInvoices(@Parameter(hidden = true) Pageable pageable) {
        Long userId = authenticationService.getCurrentUser().getId();
        return invoiceService.listInvoices(userId, pageable);
    }

    @PutMapping("/users/account")
    @Operation(summary = "Atualiza ou cria a conta de faturamento usuário logado")
    public BillingAccountDTO.BillingAccountResponse updateAccount(@Valid @RequestBody BillingAccountDTO.UpdateBillingAccountRequest request) {
        Long userId = authenticationService.getCurrentUser().getId();
        return accountService.updateOrCreate(userId, request);
    }

    @GetMapping("/subscription-status")
    @Operation(summary = "Detalhe de assinatura do usuário")
    public ResponseEntity<MySubscriptionStatusResponse> getMySubscriptionStatus() {
        return ResponseEntity.ok(billingQueryService.getMySubscriptionStatus());
    }

}
