package com.brainbyte.easy_maintenance.billing.infrastructure.web;

import com.brainbyte.easy_maintenance.billing.application.dto.InvoiceDTO;
import com.brainbyte.easy_maintenance.billing.application.dto.PaymentResponse;
import com.brainbyte.easy_maintenance.billing.application.service.InvoiceService;
import com.brainbyte.easy_maintenance.billing.application.service.PaymentService;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.billing.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/me/billing")
@Tag(name = "Billing User", description = "Operações de faturamento para o usuário autenticado")
public class UserBillingController {

    private final InvoiceService invoiceService;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

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

    @GetMapping("/payment")
    @PageableAsQueryParam
    @Operation(summary = "Lista e filtra pagamentos de forma paginada do usuário")
    public PageResponse<PaymentResponse> listPayments(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentProvider provider,
            @Parameter(hidden = true) Pageable pageable) {

        return paymentService.listPayments(userId, startDate, endDate, status, provider, pageable);
    }

    private Long getCurrentUserId() {
        String email = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return userRepository.findByEmail(email)
                .map(com.brainbyte.easy_maintenance.org_users.domain.User::getId)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado no banco de dados"));
    }
}
