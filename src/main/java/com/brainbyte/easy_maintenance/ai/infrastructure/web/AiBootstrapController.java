package com.brainbyte.easy_maintenance.ai.infrastructure.web;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapApplyResponse;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiJobAcceptedResponse;
import com.brainbyte.easy_maintenance.ai.application.service.AiBootstrapService;
import com.brainbyte.easy_maintenance.ai.application.service.AiJobService;
import com.brainbyte.easy_maintenance.ai.domain.enums.AiJobType;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.kernel.tenant.TenantContext;
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
@RequestMapping("/easy-maintenance/api/v1/ai/bootstrap")
@Tag(name = "AI Bootstrap (Onboarding)", description = "Geração de preview de cadastros iniciais via IA")
public class AiBootstrapController {

    private final AiBootstrapService bootstrapService;
    private final AiJobService aiJobService;

    @PostMapping("/preview")
    @RateLimit("ai")
    @RequireTenant
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Submete geração de preview de onboarding por IA",
            description = "Retorna 202 com jobId imediatamente. Consulte GET /ai/jobs/{jobId} para obter o preview."
    )
    public AiJobAcceptedResponse preview(@Validated @RequestBody AiBootstrapPreviewRequest request) {
        String orgCode = TenantContext.get().orElseThrow();
        String jobId = aiJobService.submitJob(orgCode, AiJobType.BOOTSTRAP_PREVIEW, request);
        return new AiJobAcceptedResponse(jobId);
    }

    @PostMapping("/apply")
    @RequireTenant
    @Operation(summary = "Aplica os cadastros gerados pela IA no banco de dados")
    public ResponseEntity<AiBootstrapApplyResponse> apply(@Validated @RequestBody AiBootstrapApplyRequest request) {
        return ResponseEntity.ok(bootstrapService.apply(request));
    }
}
