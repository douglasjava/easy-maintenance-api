package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.MaintenanceResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.RegisterMaintenanceRequest;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceService;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/items/{itemId}/maintenances")
public class MaintenancesController {

  private final MaintenanceService service;

  @PostMapping
  @RequireTenant
  @ResponseStatus(HttpStatus.CREATED)
  public MaintenanceResponse register(@PathVariable Long itemId,
                                                      @Valid @RequestBody RegisterMaintenanceRequest req) {
    String orgId = TenantContext.get().orElseThrow();
    return service.register(orgId, itemId, req);
  }

  @GetMapping
  @RequireTenant
  public Page<MaintenanceResponse> list(@PathVariable Long itemId, Pageable pageable) {
    String orgId = TenantContext.get().orElseThrow();
    return service.listByItem(orgId, itemId, pageable);
  }

}
