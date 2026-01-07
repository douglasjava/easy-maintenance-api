package com.brainbyte.easy_maintenance.dashboard.infrastructure.web;

import com.brainbyte.easy_maintenance.dashboard.application.DashboardService;
import com.brainbyte.easy_maintenance.dashboard.infrastructure.web.dto.DashboardResponse;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/dashboard")
@Validated
@Tag(name = "Dashboard", description = "KPIs e visão geral do status de manutenções")
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    @RequireTenant
    @Operation(
            summary = "Dashboard de manutenção",
            description = "Retorna KPIs, itens que requerem atenção, calendário, quebras e ações rápidas.",
            parameters = {
                    @Parameter(name = "X-Org-Id", description = "Identificador da organização (UUID)", in = ParameterIn.HEADER, required = true),
                    @Parameter(name = "daysAhead", description = "Janela para eventos futuros (1..365). Default 30.", in = ParameterIn.QUERY),
                    @Parameter(name = "nearDueThresholdDays", description = "Limite para NEAR_DUE (1..60). Default 7.", in = ParameterIn.QUERY),
                    @Parameter(name = "limitAttention", description = "Quantidade máxima de itens na lista de atenção (1..20). Default 5.", in = ParameterIn.QUERY)
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = DashboardResponse.class)))
            }
    )
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int daysAhead,
            @RequestParam(defaultValue = "7") @Min(1) @Max(60) int nearDueThresholdDays,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int limitAttention
    ) {
        String orgId = TenantContext.get().orElseThrow();
        DashboardResponse resp = service.getDashboard(orgId, daysAhead, nearDueThresholdDays, limitAttention);
        return ResponseEntity.ok(resp);
    }
}
