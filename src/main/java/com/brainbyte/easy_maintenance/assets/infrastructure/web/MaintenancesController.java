package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceService;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/items")
@Tag(name = "Ativos")
public class MaintenancesController {

  private final MaintenanceService service;

  @PostMapping("/{itemId}/maintenances")
  @RequireTenant
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Registra uma nova manutenção para um item")
  public MaintenanceResponse register(@PathVariable Long itemId,
                                      @Valid @RequestBody RegisterMaintenanceRequest req) {
    String orgId = TenantContext.get().orElseThrow();
    return service.register(orgId, itemId, req);
  }

  @GetMapping("/maintenances")
  @RequireTenant
  @PageableAsQueryParam
  @Operation(summary = "Lista o histórico de manutenções de uma organização")
  public Page<MaintenanceResponse> list(@RequestParam(required = false) Long itemId,
                                        @RequestParam(required = false) LocalDate performedAt,
                                        @RequestParam(required = false) String issuedBy,
                                        @Parameter(hidden = true) Pageable pageable) {
    String orgId = TenantContext.get().orElseThrow();
    return service.listByItem(orgId, itemId, performedAt, issuedBy, pageable);
  }

}
