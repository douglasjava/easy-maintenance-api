package com.brainbyte.easy_maintenance.org_users.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class UserDTO {

  public record CreateUserRequest(
          @Email @NotBlank String email,
          @NotBlank String name,
          @NotNull Role role,
          @NotNull Status status,
          @NotBlank String password
  ) {}

  public record UpdateUserRequest(
          String name,
          Role role,
          Status status
  ) {}

  public record UserResponse(
          String id,
          String organizationCode,
          String email,
          String name,
          Role role,
          Status status
  ) {}

  public record LoginRequest(
          @Email @NotBlank String email,
          @NotBlank String password
  ) {}

  public record LoginResponse(
          String id,
          String organizationCode,
          String email,
          String name,
          Role role,
          Status status,
          String accessToken,
          String tokenType
  ) {}

}
