package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.specifications.UserSpecifications;
import com.brainbyte.easy_maintenance.org_users.mapper.IUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.brainbyte.easy_maintenance.shared.security.JwtService;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static org.springframework.util.StringUtils.hasLength;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsersService {

    public static final String USER_NOT_FOUND_MESSAGE = "Usuário com id %s não encontrado";

    private final OrganizationsService organizationsService;
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FirstAccessTokenService firstAccessTokenService;


    public UserDTO.UserResponse createUser(UserDTO.CreateUserRequest request) {
        log.info("Creating user {} ", request.email());

        validateUserRegistered(request);

        var user = IUserMapper.INSTANCE.toUser(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        user = repository.save(user);

        return IUserMapper.INSTANCE.toUserResponse(user);

    }

    private void validateUserRegistered(UserDTO.CreateUserRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new ConflictException(String.format("E-mail %s já está em uso", request.email()));
        }
    }

    @Transactional
    public UserDTO.UserResponse createUserWithOrganization(UserDTO.CreateUserRequest request, String orgCode) {
        log.info("Creating user {} - orgId: {}", request.email(), orgCode);

        validateUserRegistered(request);

        var user = IUserMapper.INSTANCE.toUser(request);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        UserOrganization uo = UserOrganization.builder()
                .user(user)
                .organizationCode(orgCode)
                .build();
        user.getOrganizations().add(uo);

        user = repository.save(user);

        return IUserMapper.INSTANCE.toUserResponse(user);

    }

    public UserDTO.UserResponse findById(Long id) {

        log.info("Getting user with id {}", id);
        return repository.findByIdFetchOrganization(id).map(IUserMapper.INSTANCE::toUserResponse)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, id)));

    }

    public UserDTO.UserResponse findById(Long id, String orgId) {

        log.info("Getting user with id {} and code {}", id, orgId);
        return repository.findByOrganizationCodeAndId(orgId, id).map(IUserMapper.INSTANCE::toUserResponse)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, id)));

    }

    public UserDTO.UserResponse updateUser(Long id, UserDTO.UpdateUserRequest request) {
        log.info("Updating user with id {}", id);

        var user = repository.findById(id).orElseThrow(() -> new NotFoundException(
                String.format(USER_NOT_FOUND_MESSAGE, id)));

        return updateUserDetails(request, user);

    }

    public UserDTO.UserResponse updateUserWithOrgId(Long id, UserDTO.UpdateUserRequest request, String orgId) {
        log.info("Updating user with id {} e org {}", id, orgId);

        var user = repository.findByOrganizationCodeAndId(orgId, id).orElseThrow(() -> new NotFoundException(
                String.format(USER_NOT_FOUND_MESSAGE, id)));

        if (hasLength(request.email())) {
            user.setEmail(request.email());
        }

        return updateUserDetails(request, user);

    }

    public PageResponse<UserDTO.UserResponse> listAll(String name, String email, Pageable pageable) {
        log.info("Listing all users with filters");

        var spec = Specification.allOf(
                UserSpecifications.withNameLike(name),
                UserSpecifications.withEmailLike(email)
        );

        Page<UserDTO.UserResponse> page =
                repository.findAllFetchOrganization(spec, pageable).map(IUserMapper.INSTANCE::toUserResponse);

        return PageResponse.of(page);
    }

    public PageResponse<UserDTO.UserResponse> listAll(String orgId, Pageable pageable) {

        log.info("Listing all users by organization id: {}", orgId);

        Page<UserDTO.UserResponse> page =
                repository.findAllByOrganizationCode(orgId, pageable).map(IUserMapper.INSTANCE::toUserResponse);

        return PageResponse.of(page);

    }

    @Transactional(readOnly = true)
    public UserDTO.LoginResponse authenticate(UserDTO.LoginRequest request) {
        log.info("Authenticating user {}", request.email());

        var user = repository.findByEmail(request.email())
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
        }

        List<String> orgCodes = user.getOrganizations().stream()
                .map(UserOrganization::getOrganizationCode)
                .collect(Collectors.toList());

        var claims = new java.util.HashMap<String, Object>();
        claims.put("uid", user.getId());
        claims.put("orgs", orgCodes); // Passando lista de organizações
        if (!orgCodes.isEmpty()) {
            claims.put("org", orgCodes.getFirst()); // Mantendo compatibilidade com o que espera um único org
        }
        claims.put("role", user.getRole().name());
        String token = jwtService.generate(user.getEmail(), claims);

        boolean firstAccess = firstAccessTokenService.existsByUserIdAndUsedAtIsNull(user.getId());

        UserDTO.LoginResponse response = IUserMapper.INSTANCE.toLoginResponse(user);
        return new UserDTO.LoginResponse(
                response.id(),
                response.organizationCodes(),
                response.email(),
                response.name(),
                response.role(),
                response.status(),
                token,
                "Bearer",
                firstAccess
        );
    }

    @Transactional
    public void addOrganization(Long userId, String orgCode) {
        log.info("Adding organization {} to user {}", orgCode, userId);
        var user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, userId)));

        if (user.getOrganizations().stream().anyMatch(uo -> uo.getOrganizationCode().equals(orgCode))) {
            return;
        }

        UserOrganization uo = UserOrganization.builder()
                .user(user)
                .organizationCode(orgCode)
                .build();
        user.getOrganizations().add(uo);
        repository.save(user);
    }

    @Transactional
    public void removeOrganization(Long userId, String orgCode) {
        log.info("Removing organization {} from user {}", orgCode, userId);
        var user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, userId)));

        user.getOrganizations().removeIf(uo -> uo.getOrganizationCode().equals(orgCode));
        repository.save(user);
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

    public void resetPassword(User user, String newPassword) {
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(Instant.now());
        repository.save(user);
    }

    @Transactional(readOnly = true)
    public List<OrganizationDTO.OrganizationResponse> listUserOrganizations(Long userId) {
        log.info("Listing all organizations for user id: {}", userId);
        var user = repository.findByIdWithOrganization(userId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, userId)));

        var codes = user.getOrganizations().stream()
                .map(UserOrganization::getOrganizationCode)
                .toList();

        return organizationsService.listAllByCodes(codes);
    }

    private UserDTO.UserResponse updateUserDetails(UserDTO.UpdateUserRequest request, User user) {

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

}
