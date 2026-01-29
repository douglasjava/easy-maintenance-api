package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.org_users.mapper.IUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final UserOrganizationRepository userOrganizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FirstAccessTokenService firstAccessTokenService;

    @Transactional
    public UserDTO.UserResponse createUser(UserDTO.CreateUserRequest request, String orgCode) {
        log.info("Creating user {} - orgId: {}", request.email(), orgCode);

        if (repository.existsByEmail(request.email())) {
            throw new ConflictException(String.format("E-mail %s já está em uso", request.email()));
        }

        var user = IUserMapper.INSTANCE.toUser(request, orgCode);
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

    public UserDTO.UserResponse findById(Long id, String orgId) {

        log.info("Getting user with id {}", id);
        return repository.findByOrganizationCodeAndId(orgId, id).map(IUserMapper.INSTANCE::toUserResponse)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, id)));

    }

    public UserDTO.UserResponse updateUser(Long id, UserDTO.UpdateUserRequest request, String orgId) {
        log.info("Updating user with id {}", id);

        var user = repository.findByOrganizationCodeAndId(orgId, id).orElseThrow(() -> new NotFoundException(
                String.format(USER_NOT_FOUND_MESSAGE, id)));

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
            claims.put("org", orgCodes.get(0)); // Mantendo compatibilidade com o que espera um único org
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

}
