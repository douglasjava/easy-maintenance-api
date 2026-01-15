package com.brainbyte.easy_maintenance.admin.infrastucture.web;

import com.brainbyte.easy_maintenance.admin.application.service.AdminService;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/easy-maintenance/api/v1/private/admin")
@Tag(name = "Admin", description = "Operações administrativas de nível de sistema (Bootstrap)")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/ping")
    @Operation(summary = "Verifica se o serviço admin está online")
    public String ping() {
        return "ok";
    }

    @GetMapping("/validate-token")
    @Operation(summary = "Valida o token de administrador")
    public void validateTokenAdmin(@RequestHeader("X-Admin-Token") String token) {
        adminService.validateToken(token);
    }

    @PostMapping("/organizations")
    @Operation(summary = "Cria uma nova organização")
    public OrganizationDTO.OrganizationResponse createOrganization(@Valid @RequestBody OrganizationDTO.CreateOrganizationRequest request) {
        return adminService.createOrganization(request);
    }

    @PostMapping("/users/{orgCode}")
    @Operation(summary = "Cria um novo usuário para uma organização específica")
    public UserDTO.UserResponse createUser(@Valid @RequestBody UserDTO.CreateUserRequest request, @PathVariable String orgCode) {
        return adminService.createUser(request, orgCode);
    }

    @GetMapping("/organizations")
    @PageableAsQueryParam
    @Operation(summary = "Lista todas as organizações cadastradas")
    public PageResponse<OrganizationDTO.OrganizationResponse> listAll(@Parameter(hidden = true) Pageable pageable) {
        return adminService.listAllOrganizations(pageable);
    }

}
