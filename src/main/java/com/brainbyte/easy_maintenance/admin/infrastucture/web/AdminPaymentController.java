package com.brainbyte.easy_maintenance.admin.infrastucture.web;

import com.brainbyte.easy_maintenance.payment.application.dto.PaymentResponse;
import com.brainbyte.easy_maintenance.payment.application.service.PaymentService;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/private/admin/payments")
@Tag(name = "Payment Admin", description = "Operações administrativas de pagamentos")
public class AdminPaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PageableAsQueryParam
    @Operation(summary = "Lista e filtra pagamentos de forma paginada")
    public PageResponse<PaymentResponse> listPayments(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentProvider provider,
            @Parameter(hidden = true) Pageable pageable) {
        
        return paymentService.listPayments(userId, startDate, endDate, status, provider, pageable);
    }

}
