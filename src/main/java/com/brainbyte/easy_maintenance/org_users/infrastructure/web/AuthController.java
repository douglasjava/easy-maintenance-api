package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.LoginRequest;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO.LoginResponse;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/auth")
@Tag(name = "Autenticação", description = "Endpoints para login e gerenciamento de senha")
public class AuthController {

  private final UsersService usersService;

  @PostMapping("/login")
  @Operation(summary = "Realiza a autenticação do usuário")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return usersService.authenticate(request);
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Altera a senha do usuário (Primeiro acesso)")
  public void changePassword(@Valid @RequestBody UserDTO.ChangePasswordRequest request) {
    usersService.changePassword(request);
  }

}
