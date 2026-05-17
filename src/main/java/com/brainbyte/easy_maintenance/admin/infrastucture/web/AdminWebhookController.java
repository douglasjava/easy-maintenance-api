package com.brainbyte.easy_maintenance.admin.infrastucture.web;

import com.brainbyte.easy_maintenance.webhooks.commons.domain.WebhookDlqEntry;
import com.brainbyte.easy_maintenance.webhooks.commons.service.WebhookDlqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/private/admin/webhooks")
@Tag(name = "Webhook Admin", description = "Administração de webhooks e DLQ")
public class AdminWebhookController {

    private final WebhookDlqService webhookDlqService;

    @GetMapping("/dlq")
    @Operation(summary = "Lista entradas pendentes na DLQ (sem replayed_at)")
    public Page<WebhookDlqEntry> listDlq(@PageableDefault(size = 20) Pageable pageable) {
        return webhookDlqService.listPending(pageable);
    }

    @PostMapping("/dlq/{id}/replay")
    @Operation(summary = "Realiza o replay de um evento na DLQ")
    public ResponseEntity<Void> replay(@PathVariable Long id) {
        webhookDlqService.replay(id);
        return ResponseEntity.noContent().build();
    }
}
