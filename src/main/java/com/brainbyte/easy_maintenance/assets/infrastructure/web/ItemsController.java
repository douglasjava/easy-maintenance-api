package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceItemService;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/items")
@Tag(name = "Ativos", description = "Gerenciamento de itens e manutenções")
public class ItemsController {

    private final MaintenanceItemService service;

    @PostMapping
    @RequireTenant
    @Operation(summary = "Cria um novo item de manutenção")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest req) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.create(orgId, req));
    }

    @PostMapping("/batch")
    @RequireTenant
    @Operation(summary = "Cria novos itens de manutenção em lote")
    public ResponseEntity<List<ItemResponse>> createBatch(@Valid @RequestBody List<CreateItemRequest> req) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.createBatch(orgId, req));
    }

    @GetMapping
    @RequireTenant
    @PageableAsQueryParam
    @Operation(summary = "Lista itens de manutenção da organização")
    public Page<ItemResponse> list(@RequestParam(required = false) ItemStatus status,
                                   @RequestParam(required = false) String itemType,
                                   @RequestParam(required = false) ItemCategory categoria,
                                   @Parameter(hidden = true) Pageable pageable) {
        String orgId = TenantContext.get().orElseThrow();
        return service.findAll(orgId, status, itemType, categoria, pageable);
    }

    @GetMapping("/{id}")
    @RequireTenant
    @Operation(summary = "Busca um item de manutenção pelo ID")
    public ResponseEntity<ItemResponse> get(@PathVariable("id") Long id) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.findById(orgId, id));
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    @RequireTenant
    @Operation(summary = "Cria um novo item de manutenção")
    public void delete(@PathVariable("id") Long id) {
        String orgId = TenantContext.get().orElseThrow();
        service.remove(orgId, id);
    }

}
