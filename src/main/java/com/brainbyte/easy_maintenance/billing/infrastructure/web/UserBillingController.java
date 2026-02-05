package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing")
@Tag(name = "Billing User", description = "Operações de faturamento para o usuário autenticado")
public class UserBillingController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;

    @GetMapping("/summary")
    @Operation(summary = "Retorna o resumo de faturamento do usuário logado")
    public InvoiceDTO.BillingSummaryResponse getSummary() {
        Long userId = getCurrentUserId();
        return invoiceService.getSummary(userId);
    }

    @GetMapping("/invoices")
    @PageableAsQueryParam
    @Operation(summary = "Lista as faturas do usuário logado de forma paginada")
    public PageResponse<InvoiceDTO.InvoiceResponse> listInvoices(@Parameter(hidden = true) Pageable pageable) {
        Long userId = getCurrentUserId();
        return invoiceService.listInvoices(userId, pageable);
    }

    private Long getCurrentUserId() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
                .map(com.brainbyte.easy_maintenance.org_users.domain.User::getId)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado no banco de dados"));
    }
}
