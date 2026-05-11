package com.brainbyte.easy_maintenance.ai.infrastructure.web;

import com.brainbyte.easy_maintenance.ai.application.dto.AiJobAcceptedResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiJobStatusResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSuggestItemRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiSummaryResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiAssistantRequest;
import com.brainbyte.easy_maintenance.ai.application.service.AiJobService;
import com.brainbyte.easy_maintenance.ai.application.service.AiService;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobType;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.shared.ratelimit.RateLimit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/ai")
@Tag(name = "Inteligência Artificial", description = "Assistente de manutenção e sugestões via IA")
public class AiController {

    private final AiService aiService;
    private final AiJobService aiJobService;
    private final AuthenticationService authenticationService;

    @GetMapping("/summary")
    @RateLimit("ai")
    @RequireTenant
    @Operation(summary = "Obtém um resumo executivo da organização (IA ativada apenas com pretty=true)")
    public ResponseEntity<AiSummaryResponse> summary(
            @RequestParam(name = "pretty", defaultValue = "false") boolean pretty) {
        return ResponseEntity.ok(aiService.getSummary(pretty));
    }

    @PostMapping("/assistant")
    @RateLimit("ai")
    @RequireTenant
    @Operation(
            summary = "Submete pergunta ao assistente de manutenção",
            description = "Retorna 202 com jobId imediatamente. Consulte GET /ai/jobs/{jobId} para obter a resposta."
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobAcceptedResponse assistant(@Validated @RequestBody AiAssistantRequest req) {
        String orgCode = TenantContext.get().orElseThrow();
        Long userId = authenticationService.getCurrentUser().getId();
        String jobId = aiJobService.submitJob(orgCode, userId, AiJobType.ASSISTANT, req);
        return new AiJobAcceptedResponse(jobId);
    }

    @PostMapping("/suggest-item")
    @RateLimit("ai")
    @RequireTenant
    @Operation(
            summary = "Sugere configurações para um novo item de manutenção",
            description = "Retorna 202 com jobId imediatamente. Consulte GET /ai/jobs/{jobId} para obter as sugestões."
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AiJobAcceptedResponse suggestItem(@Validated @RequestBody AiSuggestItemRequest req) {
        String orgCode = TenantContext.get().orElseThrow();
        Long userId = authenticationService.getCurrentUser().getId();
        String jobId = aiJobService.submitJob(orgCode, userId, AiJobType.SUGGEST_ITEM, req);
        return new AiJobAcceptedResponse(jobId);
    }

    @GetMapping("/jobs/{jobId}")
    @RequireTenant
    @Operation(
            summary = "Consulta o status de um job de IA",
            description = "Retorna PENDING, PROCESSING, DONE ou FAILED. Resultado disponível por 24h após DONE."
    )
    public ResponseEntity<AiJobStatusResponse> getJobStatus(@PathVariable String jobId) {
        String orgCode = TenantContext.get().orElseThrow();
        return ResponseEntity.ok(aiJobService.getJobStatus(jobId, orgCode));
    }
}
