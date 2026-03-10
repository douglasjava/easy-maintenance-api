package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO.CreateOrganizationRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO.OrganizationResponse;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO.UpdateOrganizationRequest;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/organizations")
@Tag(name = "Organizações", description = "Gerenciamento de organizações")
public class OrganizationsController {

    private final OrganizationsService service;
    private final UsersService usersService;

    @PostMapping
    @Operation(summary = "Cria uma nova organização para um usuário")
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma organização pelo ID")
    public OrganizationResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping
    @RequireTenant
    @PageableAsQueryParam
    @Operation(summary = "Lista todas as organizações (Requer Tenant)")
    public PageResponse<OrganizationResponse> listAll(@RequestParam(required = false) String name,
                                                      @RequestParam(required = false) Plan plan,
                                                      @RequestParam(required = false) String city,
                                                      @RequestParam(required = false) String doc,
                                                      @Parameter(hidden = true) Pageable pageable) {
        return service.listAll(name, plan, city, doc, pageable);
    }

    @PatchMapping("/{id}")
    @RequireTenant
    @Operation(summary = "Atualiza dados de uma organização")
    public OrganizationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateOrganizationRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{orgCode}/users/{userId}")
    @Operation(summary = "Adiciona uma organização a um usuário existente")
    public void addOrganizationToUser(@PathVariable Long userId, @PathVariable String orgCode) {
        usersService.addOrganization(userId, orgCode);
    }

    @DeleteMapping("/{orgCode}/users/{userId}")
    @Operation(summary = "Remove uma organização de um usuário")
    public void removeOrganizationToUser(@PathVariable Long userId, @PathVariable String orgCode) {
        usersService.removeOrganization(userId, orgCode);
    }

    @GetMapping("/me/{userId}")
    @Operation(summary = "Lista todas as organizações associadas ao usuário")
    public List<OrganizationDTO.OrganizationWithSubscriptionResponse> listAllOrganizationsByMe(@PathVariable Long userId) {
        return service.listUserOrganizations(userId);
    }

}
