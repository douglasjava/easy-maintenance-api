package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.kernel.tenant.RequireTenant;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.CreateUserRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.UpdateUserRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.UserResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.shared.web.openapi.PageableAsQueryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/organizations")
@Tag(name = "Organizações - Usuários", description = "Usuários vinculados a uma organização")
public class UsersController {

    private final UsersService service;

    @PostMapping("/{orgCode}/users")
    @RequireTenant
    @Operation(summary = "Cria um novo usuário na organização")
    public UserResponse create(@PathVariable String orgCode, @Valid @RequestBody CreateUserRequest req) {

        return service.createUserWithOrganization(req, orgCode);

    }

    @GetMapping("/{orgCode}/users")
    @RequireTenant
    @PageableAsQueryParam
    @Operation(summary = "Lista usuários da organização")
    public PageResponse<UserResponse> list(@PathVariable String orgCode, @Parameter(hidden = true) Pageable pageable) {

        return service.listAll(orgCode, pageable);

    }

    @GetMapping("/{orgCode}/users/{id}")
    @RequireTenant
    @Operation(summary = "Busca um usuário pelo ID")
    public UserResponse findById(@PathVariable String orgCode, @PathVariable Long id) {

        return service.findById(id, orgCode);

    }

    @PatchMapping("/{orgCode}/users/{id}")
    @RequireTenant
    @Operation(summary = "Atualiza dados de um usuário")
    public UserResponse update(@PathVariable String orgCode,
                               @PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest req) {

        return service.updateUserWithOrgId(id, req, orgCode);

    }

}
