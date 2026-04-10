package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceExportService;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceService;
import com.brainbyte.easy_maintenance.assets.domain.enums.MaintenanceType;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/items")
@Tag(name = "Manutenções", description = "Endpoints para gestão de manutenções de ativos")
public class MaintenancesController {

    private final MaintenanceService service;
    private final MaintenanceExportService exportService;

    @PostMapping("/{itemId}/maintenances")
    @RequireTenant
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Registra uma nova manutenção para um item",
            description = "Cria um registro de manutenção (preventiva, corretiva, etc) para um ativo específico.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Manutenção registrada com sucesso"),
                    @ApiResponse(responseCode = "400", description = "Dados da requisição inválidos"),
                    @ApiResponse(responseCode = "404", description = "Item não encontrado"),
                    @ApiResponse(responseCode = "409", description = "Já existe uma manutenção para este item na data atual")
            }
    )
    public MaintenanceResponse register(
            @Parameter(description = "ID do item de manutenção", example = "1") @PathVariable Long itemId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Dados para registro da manutenção",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterMaintenanceRequest.class),
                            examples = @ExampleObject(
                                    value = "{\"performedAt\": \"2024-05-20\", \"type\": \"PREVENTIVA\", \"performedBy\": \"Técnico João Silva\", \"costCents\": 15000, \"nextDueAt\": \"2024-11-20\"}"
                            )
                    )
            )
            @Valid @RequestBody RegisterMaintenanceRequest req) {
        String orgId = TenantContext.get().orElseThrow();
        return service.register(orgId, itemId, req);
    }

    @GetMapping("/maintenances")
    @RequireTenant
    @PageableAsQueryParam
    @Operation(
            summary = "Lista o histórico de manutenções de uma organização",
            description = "Retorna uma lista paginada de manutenções, permitindo filtrar por item, data, tipo ou executor."
    )
    public Page<MaintenanceResponse> list(
            @Parameter(description = "Filtrar por ID do item") @RequestParam(required = false) Long itemId,
            @Parameter(description = "Filtrar por data de execução (YYYY-MM-DD)") @RequestParam(required = false) LocalDate performedAt,
            @Parameter(description = "Filtrar por tipo de manutenção") @RequestParam(required = false) MaintenanceType type,
            @Parameter(description = "Filtrar por executor") @RequestParam(required = false) String performedBy,
            @Parameter(hidden = true) Pageable pageable) {
        String orgId = TenantContext.get().orElseThrow();
        return service.listByItem(orgId, itemId, performedAt, type, performedBy, pageable);
    }

    @GetMapping("/maintenances/export")
    @RequireTenant
    @Operation(
            summary = "Exporta manutenções em CSV",
            description = "Gera um arquivo CSV com até 5000 manutenções. Disponível apenas para planos com reportsEnabled."
    )
    public ResponseEntity<byte[]> exportCsv(
            @Parameter(description = "Filtrar por ID do item") @RequestParam(required = false) Long itemId,
            @Parameter(description = "Data de início (YYYY-MM-DD)") @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "Data de fim (YYYY-MM-DD)") @RequestParam(required = false) LocalDate endDate) {

        String orgCode = TenantContext.get().orElseThrow();
        byte[] csv = exportService.exportCsv(orgCode, itemId, startDate, endDate);

        String filename = "manutencoes_" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/maintenances/{maintenanceId}")
    @RequireTenant
    @Operation(
            summary = "Busca uma manutenção pelo ID",
            description = "Retorna os detalhes de uma manutenção específica, incluindo a lista de anexos.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Manutenção encontrada"),
                    @ApiResponse(responseCode = "404", description = "Manutenção não encontrada"),
                    @ApiResponse(responseCode = "403", description = "Acesso negado")
            }
    )
    public MaintenanceResponse findById(
            @Parameter(description = "ID da manutenção", example = "1") @PathVariable Long maintenanceId) {
        String orgId = TenantContext.get().orElseThrow();
        return service.findById(orgId, maintenanceId);
    }

}
