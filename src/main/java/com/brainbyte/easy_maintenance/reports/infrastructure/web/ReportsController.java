package com.brainbyte.easy_maintenance.reports.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceExportService;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsMaintenanceResponse;
import com.brainbyte.easy_maintenance.reports.application.dto.ReportsOverviewResponse;
import com.brainbyte.easy_maintenance.reports.application.service.ReportsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/reports")
@Tag(name = "Relatórios", description = "Dashboard consolidado e relatórios cross-org do usuário autenticado")
public class ReportsController {

    private final ReportsService reportsService;
    private final MaintenanceExportService exportService;
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

    @GetMapping("/maintenances")
    @Operation(
            summary = "Listagem paginada de manutenções cross-org",
            description = "Retorna manutenções de todas as empresas do usuário. "
                    + "Filtros opcionais: orgCodes (padrão: todas as empresas do usuário), "
                    + "performedAtFrom, performedAtTo, type, itemType. "
                    + "Cada registro inclui orgCode e orgName para identificação da empresa."
    )
    public PageResponse<ReportsMaintenanceResponse> listMaintenances(
            @Parameter(description = "Filtrar por empresas específicas (códigos). Padrão: todas as empresas do usuário.")
            @RequestParam(required = false) List<String> orgCodes,
            @Parameter(description = "Data de início do período (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate performedAtFrom,
            @Parameter(description = "Data de fim do período (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate performedAtTo,
            @Parameter(description = "Tipo de manutenção")
            @RequestParam(required = false) MaintenanceType type,
            @Parameter(description = "Tipo do item (ex: EXTINTOR, GERADOR)")
            @RequestParam(required = false) String itemType,
            @PageableDefault(size = 20, sort = "performedAt") Pageable pageable) {
        var user = authenticationService.getCurrentUser();
        return reportsService.listMaintenances(user.getId(), orgCodes, performedAtFrom, performedAtTo, type, itemType, pageable);
    }

    @GetMapping("/maintenances/export")
    @Operation(
            summary = "Exportar manutenções cross-org em CSV",
            description = "Gera um CSV consolidado com manutenções de todas as empresas do usuário (máx. 5000 registros). "
                    + "Apenas empresas com o plano que inclui exportação de relatórios são incluídas. "
                    + "Não requer X-Org-Id — usa as organizações do usuário autenticado."
    )
    public ResponseEntity<byte[]> exportMaintenancesCrossOrg(
            @Parameter(description = "Filtrar por empresas específicas. Padrão: todas as empresas autorizadas.")
            @RequestParam(required = false) List<String> orgCodes,
            @Parameter(description = "Data de início do período (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Data de fim do período (YYYY-MM-DD)")
            @RequestParam(required = false) LocalDate endDate) {
        var user = authenticationService.getCurrentUser();
        byte[] csv = exportService.exportCsvCrossOrg(user.getId(), orgCodes, startDate, endDate);
        String filename = "relatorios_manutencoes_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
