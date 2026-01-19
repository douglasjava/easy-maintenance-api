package com.brainbyte.easy_maintenance.ai.infrastructure.web;

import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewRequest;
import com.brainbyte.easy_maintenance.ai.application.dto.AiBootstrapPreviewResponse;
import com.brainbyte.easy_maintenance.ai.application.service.AiBootstrapService;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/ai/bootstrap")
@Tag(name = "AI Bootstrap (Onboarding)", description = "Geração de preview de cadastros iniciais via IA")
public class AiBootstrapController {

    private final AiBootstrapService bootstrapService;

    @PostMapping("/preview")
    @RequireTenant
    @Operation(summary = "Gera um preview de itens de manutenção para onboarding baseado no tipo de empresa")
    public ResponseEntity<AiBootstrapPreviewResponse> preview(@Validated @RequestBody AiBootstrapPreviewRequest request) {
        return ResponseEntity.ok(bootstrapService.preview(request));
    }
}
