package com.brainbyte.easy_maintenance.admin.infrastucture.web;

import com.brainbyte.easy_maintenance.admin.application.dto.AdminMetricsResponse;
import com.brainbyte.easy_maintenance.admin.application.service.AdminService;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
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

    @GetMapping("/metrics")
    @Operation(summary = "Recupera métricas do sistema")
    public AdminMetricsResponse getMetrics() {
        return adminService.getMetrics();
    }

    @PostMapping("/organizations")
    @Operation(summary = "Cria uma nova organização")
    public OrganizationDTO.OrganizationResponse createOrganization(@Valid @RequestBody OrganizationDTO.CreateOrganizationRequest request) {
        return adminService.createOrganization(request);
    }

    @GetMapping("/organizations")
    @PageableAsQueryParam
    @Operation(summary = "Lista todas as organizações cadastradas")
    public PageResponse<OrganizationDTO.OrganizationResponse> listAllOrganizations(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Plan plan,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String doc,
            @Parameter(hidden = true) Pageable pageable) {
        return adminService.listAllOrganizations(name, plan, city, doc, pageable);
    }

    @GetMapping("/organizations/{id}")
    @PageableAsQueryParam
    @Operation(summary = "Buscar organização cadastrado por id")
    public OrganizationDTO.OrganizationResponse findByOrganizationId(@PathVariable Long id) {
        return adminService.findByOrganizationId(id);
    }

    @PostMapping("/users-with-organization/{orgCode}")
    @Operation(summary = "Cria um novo usuário para uma organização específica")
    public UserDTO.UserResponse createUserWithOrganization(@Valid @RequestBody UserDTO.CreateUserRequest request, @PathVariable String orgCode) {
        return adminService.createUserWithOrganization(request, orgCode);
    }

    @PostMapping("/users")
    @Operation(summary = "Cria um novo usuário")
    public UserDTO.UserResponse createUserWithOrganization(@Valid @RequestBody UserDTO.CreateUserRequest request) {
        return adminService.createUser(request);
    }

    @GetMapping("/users/{idUser}/organizations")
    @PageableAsQueryParam
    @Operation(summary = "Buscar organização cadastrado por id")
    public List<OrganizationDTO.OrganizationResponse> listAllOrganizationByIdUser(@PathVariable Long idUser) {
        return adminService.listAllOrganizationByIdUser(idUser);
    }

    @GetMapping("/users")
    @PageableAsQueryParam
    @Operation(summary = "Lista todos os usuários cadastrados")
    public PageResponse<UserDTO.UserResponse> listAllUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @Parameter(hidden = true) Pageable pageable) {
        return adminService.listAllUsers(name, email, pageable);
    }

    @GetMapping("/users/{id}")
    @PageableAsQueryParam
    @Operation(summary = "Buscar usuário cadastrado por id")
    public UserDTO.UserResponse findByUserId(@PathVariable Long id) {
        return adminService.findByUserId(id);
    }

    @PostMapping("/users/{userId}/organizations/{orgCode}")
    @Operation(summary = "Adiciona uma organização a um usuário existente")
    public void addOrganizationToUser(@PathVariable Long userId, @PathVariable String orgCode) {
        adminService.addOrganizationToUser(userId, orgCode);
    }

    @DeleteMapping("/users/{userId}/organizations/{orgCode}")
    @Operation(summary = "Remove uma organização de um usuário")
    public void removeOrganizationFromUser(@PathVariable Long userId, @PathVariable String orgCode) {
        adminService.removeOrganizationFromUser(userId, orgCode);
    }

    @PutMapping("/users/{id}")
    @PageableAsQueryParam
    @Operation(summary = "Alterar usuário cadastrado por id")
    public UserDTO.UserResponse updateById(@PathVariable Long id, @RequestBody UserDTO.UpdateUserRequest request) {
        return adminService.updateById(id, request);
    }

}
