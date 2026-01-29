package com.brainbyte.easy_maintenance.org_users.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class UserDTO {

  public record CreateUserRequest(
          @Schema(example = "usuario@empresa.com")
          @Email @NotBlank String email,
          @Schema(example = "João Silva")
          @NotBlank String name,
          @NotNull Role role,
          @NotNull Status status,
          @Schema(example = "senha123")
          @NotBlank String password
  ) {}

  public record UpdateUserRequest(
          @Schema(example = "João Silva Atualizado")
          String name,
          Role role,
          Status status
  ) {}

  public record UserResponse(
          Long id,
          List<String> organizationCodes,
          String email,
          String name,
          Role role,
          Status status
  ) {}

  public record LoginRequest(
          @Schema(example = "usuario@empresa.com")
          @Email @NotBlank String email,
          @Schema(example = "senha123")
          @NotBlank String password
  ) {}

  public record LoginResponse(
          Long id,
          java.util.List<String> organizationCodes,
          String email,
          String name,
          Role role,
          Status status,
          String accessToken,
          String tokenType,
          boolean firstAccess
  ) {}

  public record ChangePasswordRequest(
          @NotNull Long idUser,
          @Schema(example = "novaSenha123")
          @NotBlank String newPassword
  ) {}

  public record ForgotPasswordRequest(
          @Schema(example = "usuario@empresa.com")
          @Email @NotBlank String email
  ) {}

  public record ResetPasswordRequest(
          @NotBlank String token,
          @Schema(example = "novaSenha123")
          @NotBlank String newPassword
  ) {}

  public record AuthMessageResponse(
          String message
  ) {}

}
