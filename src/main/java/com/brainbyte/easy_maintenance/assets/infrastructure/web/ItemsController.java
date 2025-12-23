package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceItemService;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/items")
public class ItemsController {

  private final MaintenanceItemService service;

  @PostMapping
  @RequireTenant
  public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest req) {
    String orgId = TenantContext.get().orElseThrow();
    return ResponseEntity.ok(service.create(orgId, req));
  }

  @GetMapping
  @RequireTenant
  @PageableAsQueryParam
  public Page<ItemResponse> list(@RequestParam(required = false) ItemStatus status,
                                 @RequestParam(required = false) String itemType,
                                 @Parameter(hidden = true) Pageable pageable) {
    String orgId = TenantContext.get().orElseThrow();
    return service.findAll(orgId, status, itemType, pageable);
  }

  @GetMapping("/{id}")
  @RequireTenant
  public ResponseEntity<ItemResponse> get(@PathVariable("id") Long id) {
    String orgId = TenantContext.get().orElseThrow();
    return ResponseEntity.ok(service.findById(orgId, id));
  }

}
