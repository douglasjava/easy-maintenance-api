package com.brainbyte.easy_maintenance.infrastructure.access.infrastructure.web;

import com.brainbyte.easy_maintenance.infrastructure.access.application.dto.AccessContextDTO;
import com.brainbyte.easy_maintenance.infrastructure.access.application.service.FeatureAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/access-context")
@Tag(name = "Acesso", description = "Contexto de acesso por assinatura")
public class AccessContextController {

    private final FeatureAccessService featureAccessService;

    @GetMapping
    @Operation(summary = "Obter o contexto de acesso resolvido para o usuário e organização atuais")
    public AccessContextDTO getAccessContext() {
        return featureAccessService.getAccessContext();
    }
}
