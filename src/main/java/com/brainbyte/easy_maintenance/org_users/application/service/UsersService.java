package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.mapper.IUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.brainbyte.easy_maintenance.shared.security.JwtService;

import java.time.Instant;

import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasLength;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersService {

  private final OrganizationsService organizationsService;
  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final FirstAccessTokenService firstAccessTokenService;

  public UserDTO.UserResponse createUser(UserDTO.CreateUserRequest request, String orgCode) {
    log.info("Creating user {} - orgId: {}", request.email(), orgCode);

    if (repository.existsByEmail(request.email())) {
      throw new ConflictException(String.format("E-mail %s já está em uso", request.email()));
    }

    var user = IUserMapper.INSTANCE.toUser(request, orgCode);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setCreatedAt(Instant.now());
    user.setUpdatedAt(Instant.now());

    user = repository.save(user);

    return IUserMapper.INSTANCE.toUserResponse(user);

  }

  public UserDTO.UserResponse findById(Long id, String orgId) {

    log.info("Getting user with id {}", id);
    return repository.findByOrganizationCodeAndId(orgId, id).map(IUserMapper.INSTANCE::toUserResponse)
            .orElseThrow(() -> new NotFoundException(String.format("User with id %s not found", id)));

  }

  public UserDTO.UserResponse updateUser(Long id, UserDTO.UpdateUserRequest request, String orgId) {
    log.info("Updating user with id {}", id);

    var user = repository.findByOrganizationCodeAndId(orgId, id).orElseThrow(() -> new NotFoundException(
            String.format("User with id %s not found", id)));

    if (!orgId.equals(user.getOrganizationCode())) {
      throw new ConflictException(String.format("User with id %s does not belong to organization %s", id, orgId));
    }

    if (hasLength(request.name())) {
      user.setName(request.name());
    }
    if (nonNull(request.role())) {
      user.setRole(request.role());
    }
    if (nonNull(request.status())) {
      user.setStatus(request.status());
    }
    user.setUpdatedAt(Instant.now());

    var updatedUser = repository.save(user);

    return IUserMapper.INSTANCE.toUserResponse(updatedUser);

  }

  public PageResponse<UserDTO.UserResponse> listAll(String orgId, Pageable pageable) {

    log.info("Listing all users by organization id: {}", orgId);

    var userExample = User.builder().organizationCode(orgId).build();
    var example = Example.of(userExample);

    Page<UserDTO.UserResponse> page =
            repository.findAll(example, pageable).map(IUserMapper.INSTANCE::toUserResponse);

    return PageResponse.of(page);


  }

  public UserDTO.LoginResponse authenticate(UserDTO.LoginRequest request) {
    log.info("Authenticating user {}", request.email());

    var user = repository.findByEmail(request.email())
            .orElseThrow(() -> new ErrorResponseException(HttpStatus.UNAUTHORIZED));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
    }

    var claims = new java.util.HashMap<String, Object>();
    claims.put("uid", user.getId());
    claims.put("org", user.getOrganizationCode());
    claims.put("role", user.getRole().name());
    String token = jwtService.generate(user.getEmail(), claims);

    boolean firstAccess = firstAccessTokenService.existsByUserIdAndUsedAtIsNull(user.getId());

    return new UserDTO.LoginResponse(
            user.getId(),
            user.getOrganizationCode(),
            user.getEmail(),
            user.getName(),
            user.getRole(),
            user.getStatus(),
            token,
            "Bearer",
            firstAccess

    );
  }

  public void changePassword(UserDTO.ChangePasswordRequest request) {
    log.info("Mudando senha de primeiro acesso");

    var firstAccessToken = firstAccessTokenService.findByUserId(request.idUser())
            .orElseThrow(() -> new NotFoundException("Token de primeiro acesso não encontrado"));

    if (firstAccessToken.getUsedAt() != null) {
      throw new ConflictException("Token de primeiro acesso já foi utilizado");
    }

    if (firstAccessToken.getExpiresAt().isBefore(Instant.now())) {
      throw new ConflictException("Token de primeiro acesso expirado");
    }

    var user = repository.findById(firstAccessToken.getUserId())
            .orElseThrow(() -> new NotFoundException("Usuário não encontrado"));

    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    user.setUpdatedAt(Instant.now());
    repository.save(user);

    firstAccessTokenService.markUsed(firstAccessToken);

  }

}
