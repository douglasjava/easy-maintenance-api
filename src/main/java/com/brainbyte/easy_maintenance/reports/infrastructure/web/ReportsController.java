package com.brainbyte.easy_maintenance.reports.infrastructure.web;

import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsOverviewResponse;
import com.brainbyte.easy_maintenance.reports.application.service.ReportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/reports")
@Tag(name = "Relatórios", description = "Dashboard consolidado e relatórios cross-org do usuário autenticado")
public class ReportsController {

    private final ReportsService reportsService;
    private final AuthenticationService authenticationService;

    @GetMapping("/overview")
    @Operation(
            summary = "KPIs consolidados de todas as empresas do usuário",
            description = "Retorna totais globais e breakdown por empresa (organização). "
                    + "Não requer X-Org-Id — usa as organizações do usuário autenticado."
    )
    public ReportsOverviewResponse getOverview() {
        var user = authenticationService.getCurrentUser();
        return reportsService.getOverview(user.getId());
    }
}
