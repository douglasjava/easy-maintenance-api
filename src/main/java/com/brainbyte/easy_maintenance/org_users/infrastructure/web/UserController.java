package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/user")
@Tag(name = "Usuários", description = "Usuários")
public class UserController {

    private final UsersService service;
    private final AuthenticationService authenticationService;

    @GetMapping("/{id}")
    @Operation(summary = "Buscar um usuário pelo ID")
    public UserDTO.UserResponse findById( @PathVariable Long id) {

        return service.findById(id);

    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar dados de um usuário")
    public UserDTO.UserResponse update(@PathVariable Long id,
                                       @Valid @RequestBody UserDTO.UpdateUserRequest req) {

        return service.updateUser(id, req);

    }

    @GetMapping("/subscription/guard")
    @Operation(summary = "Valida se o usuário possui assinatura válida (TRIAL dentro do prazo ou não TRIAL)")
    public void validateSubscriptions() {
        var user = authenticationService.getCurrentUser();
        service.validateSubscriptions(user);
    }

}
