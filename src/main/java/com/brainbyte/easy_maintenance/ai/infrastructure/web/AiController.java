package com.brainbyte.easy_maintenance.ai.infrastructure.web;

import com.brainbyte.easy_maintenance.ai.application.service.AiService;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSummaryResponse;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/ai")
@Tag(name = "Inteligência Artificial", description = "Assistente de manutenção e sugestões via IA")
public class AiController {

    private final AiService aiService;

    @GetMapping("/summary")
    @RequireTenant
    @Operation(summary = "Obtém um resumo executivo gerado por IA")
    public ResponseEntity<AiSummaryResponse> summary(@RequestParam(name = "pretty", defaultValue = "false") boolean pretty) {
        return ResponseEntity.ok(aiService.getSummary(pretty));
    }

    @PostMapping("/assistant")
    @RequireTenant
    @Operation(summary = "Chat com o assistente de manutenção")
    public ResponseEntity<AiAssistantResponse> assistant(@Validated @RequestBody AiAssistantRequest req) {
        return ResponseEntity.ok(aiService.assistant(req));
    }

    @PostMapping("/suggest-item")
    @RequireTenant
    @Operation(summary = "Sugere configurações para um novo item de manutenção")
    public ResponseEntity<AiSuggestItemResponse> suggestItem(@Validated @RequestBody AiSuggestItemRequest req) {
        return ResponseEntity.ok(aiService.suggestItem(req));
    }
    
}
