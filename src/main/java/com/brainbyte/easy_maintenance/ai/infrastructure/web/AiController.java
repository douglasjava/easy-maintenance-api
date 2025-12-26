package com.brainbyte.easy_maintenance.ai.infrastructure.web;

import com.brainbyte.easy_maintenance.ai.application.service.AiService;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSummaryResponse;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/ai")
public class AiController {

    private final AiService aiService;

    @GetMapping("/summary")
    @RequireTenant
    public ResponseEntity<AiSummaryResponse> summary(@RequestParam(name = "pretty", defaultValue = "false") boolean pretty) {
        return ResponseEntity.ok(aiService.getSummary(pretty));
    }

    @PostMapping("/assistant")
    @RequireTenant
    public ResponseEntity<AiAssistantResponse> assistant(@Validated @RequestBody AiAssistantRequest req) {
        return ResponseEntity.ok(aiService.assistant(req));
    }

    @PostMapping("/suggest-item")
    @RequireTenant
    public ResponseEntity<AiSuggestItemResponse> suggestItem(@Validated @RequestBody AiSuggestItemRequest req) {
        return ResponseEntity.ok(aiService.suggestItem(req));
    }
}
