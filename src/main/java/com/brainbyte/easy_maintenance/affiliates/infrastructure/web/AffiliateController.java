package com.brainbyte.easy_maintenance.affiliates.infrastructure.web;

import com.brainbyte.easy_maintenance.affiliates.application.dto.AffiliateDashboardResponse;
import com.brainbyte.easy_maintenance.affiliates.application.dto.AffiliateResponse;
import com.brainbyte.easy_maintenance.affiliates.application.dto.CreateAffiliateRequest;
import com.brainbyte.easy_maintenance.affiliates.application.service.AffiliateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/easy-maintenance/api/v1/affiliates")
@RequiredArgsConstructor
@Tag(name = "Affiliates", description = "Programa de indicação — endpoints públicos")
public class AffiliateController {

    private final AffiliateService affiliateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar afiliado (Público)",
            description = "Registra um novo afiliado e retorna o código único e o link de indicação.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Afiliado cadastrado com sucesso"),
                    @ApiResponse(responseCode = "400", description = "E-mail já cadastrado ou dados inválidos")
            }
    )
    public AffiliateResponse register(@Valid @RequestBody CreateAffiliateRequest request) {
        return affiliateService.createAffiliate(request);
    }

    @GetMapping("/suggest")
    @Operation(
            summary = "Sugerir afiliado por e-mail (Público)",
            description = "Retorna o afiliado vinculado ao lead com esse e-mail, se existir."
    )
    public AffiliateResponse suggestByEmail(@RequestParam String email) {
        return affiliateService.suggestForEmail(email)
                .map(affiliateService::toResponse)
                .orElse(null);
    }

    @GetMapping("/{code}/dashboard")
    @Operation(
            summary = "Painel do afiliado (Público)",
            description = "Retorna estatísticas e lista de indicações com e-mails mascarados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Dashboard retornado"),
                    @ApiResponse(responseCode = "400", description = "Código de afiliado não encontrado")
            }
    )
    public AffiliateDashboardResponse dashboard(@PathVariable String code) {
        return affiliateService.getDashboard(code);
    }
}
