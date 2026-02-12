package com.brainbyte.easy_maintenance.leads.infrastructure.web;

import com.brainbyte.easy_maintenance.leads.application.dto.CreateLeadRequest;
import com.brainbyte.easy_maintenance.leads.application.dto.LeadResponse;
import com.brainbyte.easy_maintenance.leads.application.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/easy-maintenance/api/v1/landing/leads")
@RequiredArgsConstructor
@Tag(name = "Leads", description = "Gerenciamento de leads capturados em landing pages")
public class LeadController {

    private final LeadService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Criar novo lead (Público)",
            description = "Registra as informações de um lead capturado, incluindo metadados de marketing e rastreio.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Lead criado com sucesso")
            }
    )
    public LeadResponse create(@Valid @RequestBody CreateLeadRequest request, HttpServletRequest httpRequest) {
        return service.createLead(request, httpRequest);
    }
}
