package com.brainbyte.easy_maintenance.assets.infrastructure.web;

import com.brainbyte.easy_maintenance.assets.application.dto.CreateItemRequest;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemPermissionResponse;
import com.brainbyte.easy_maintenance.assets.application.dto.ItemResponse;
import com.brainbyte.easy_maintenance.assets.application.service.MaintenanceItemService;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemCategory;
import com.brainbyte.easy_maintenance.assets.domain.enums.ItemStatus;
import com.brainbyte.easy_maintenance.assets.application.service.ItemCalendarExportService;
import com.brainbyte.easy_maintenance.commons.dto.CursorPageResponse;
import com.brainbyte.easy_maintenance.infrastructure.access.infrastructure.security.RequiresFullAccess;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/items")
@Tag(name = "Ativos", description = "Gerenciamento de itens e manutenções")
public class ItemsController {

    private final MaintenanceItemService service;
    private final ItemCalendarExportService calendarExportService;

    @PostMapping
    @RequireTenant
    @RequiresFullAccess
    @Operation(summary = "Cria um novo item de manutenção")
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody CreateItemRequest req) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.create(orgId, req));
    }

    @PostMapping("/batch")
    @RequireTenant
    @RequiresFullAccess
    @Operation(summary = "Cria novos itens de manutenção em lote")
    public ResponseEntity<List<ItemResponse>> createBatch(@Valid @RequestBody List<CreateItemRequest> req) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.createBatch(orgId, req));
    }

    @GetMapping
    @RequireTenant
    @Operation(summary = "Lista itens de manutenção da organização. Suporta cursor pagination (cursor/prevCursor) e OFFSET (page/size).")
    public CursorPageResponse<ItemResponse> list(
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) ItemCategory categoria,
            @Parameter(description = "Cursor para paginação forward (ID do último item visto)") @RequestParam(required = false) Long cursor,
            @Parameter(description = "Cursor para paginação backward (ID do primeiro item da página atual)") @RequestParam(required = false) Long prevCursor,
            @RequestParam(defaultValue = "20") int size) {
        String orgId = TenantContext.get().orElseThrow();
        return service.findAllCursor(orgId, status, itemType, categoria, cursor, prevCursor, size);
    }

    @GetMapping("/calendar")
    @RequireTenant
    @Operation(
            summary = "Lista todos os itens da organização com nextDueAt dentro do intervalo informado",
            description = "Visão de calendário (TASK-124): sem paginação cursor, itens sem nextDueAt não entram no resultado."
    )
    public ResponseEntity<List<ItemResponse>> calendar(
            @Parameter(description = "Início do intervalo (inclusive)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Fim do intervalo (inclusive)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) ItemCategory categoria) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.findAllForCalendar(orgId, fromDate, toDate, status, itemType, categoria));
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
    @RequiresFullAccess
    @Operation(summary = "Cria um novo item de manutenção")
    public void delete(@PathVariable("id") Long id) {
        String orgId = TenantContext.get().orElseThrow();
        service.remove(orgId, id);
    }

    @PutMapping("/{id}")
    @RequireTenant
    @RequiresFullAccess
    @Operation(summary = "Editar item de manutenção")
    public ResponseEntity<ItemResponse> update(@PathVariable("id") Long id, @RequestBody CreateItemRequest request) {
        String orgId = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(service.update(orgId, id, request));
    }


    @GetMapping("/{id}/can-update")
    @RequireTenant
    @Operation(summary = "Validar edição item de manutenção")
    public ResponseEntity<ItemPermissionResponse> validateUpdate(@PathVariable("id") Long id) {

        return ResponseEntity.ok(service.isEditable(id));

    }

    @GetMapping("/{id}/calendar.ics")
    @RequireTenant
    @Operation(
            summary = "Exporta o vencimento do item como lembrete .ics",
            description = "Gera um arquivo .ics (2 lembretes: 7 dias e 1 dia antes) para importar em qualquer app de calendário. Ver TASK-123."
    )
    public ResponseEntity<byte[]> exportCalendar(@PathVariable("id") Long id) {
        String orgId = TenantContext.get().orElseThrow();
        byte[] ics = calendarExportService.exportIcs(orgId, id);

        String filename = "item-" + id + "-lembrete.ics";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .body(ics);
    }

}
