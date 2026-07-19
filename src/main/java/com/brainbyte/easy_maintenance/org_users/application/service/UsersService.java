package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.billing.application.service.BillingPlanFeaturesHelper;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItem;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.ConflictException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.commons.utils.PhoneNumberNormalizer;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
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

    private static final String SCOPE_2FA_PENDING = "2fa_pending";
    private static final long PENDING_TOKEN_TTL_SECONDS = 300; // 5 minutes

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final FirstAccessTokenService firstAccessTokenService;
    private final TwoFactorService twoFactorService;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final BillingPlanFeaturesHelper billingPlanFeaturesHelper;
    private final UserOrganizationRepository userOrganizationRepository;
    private final BillingSubscriptionService billingSubscriptionService;

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

    public void applyReferralCode(Long userId, String referralCode) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + userId));
        user.setReferralCode(referralCode);
        repository.save(user);
        log.info("[Affiliate] referralCode={} applied to userId={}", referralCode, userId);
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
                repository.findAll(spec, pageable).map(IUserMapper.INSTANCE::toUserResponse);

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

        // 2FA check: if enabled, return pending challenge instead of full JWT
        if (twoFactorService.isEnabled(user.getId())) {
            var pendingClaims = new java.util.HashMap<String, Object>();
            pendingClaims.put("uid", user.getId());
            pendingClaims.put("scope", SCOPE_2FA_PENDING);
            String pendingToken = jwtService.generateWithTtl(user.getEmail(), pendingClaims, PENDING_TOKEN_TTL_SECONDS);
            log.info("2FA challenge issued for user {}", user.getId());
            return new UserDTO.LoginResponse(
                    user.getId(), orgCodes, user.getEmail(), user.getName(),
                    user.getRole(), user.getStatus(),
                    null, null, false, true, pendingToken);
        }

        boolean firstAccess = firstAccessTokenService.existsByUserIdAndUsedAtIsNull(user.getId());
        String token = buildFullToken(user, orgCodes);
        return new UserDTO.LoginResponse(
                user.getId(), orgCodes, user.getEmail(), user.getName(),
                user.getRole(), user.getStatus(),
                token, "Bearer", firstAccess, false, null);
    }

    public UserDTO.LoginResponse verifyTwoFactor(UserDTO.TwoFactorVerifyRequest request) {
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> parsed;
        try {
            parsed = jwtService.parse(request.pendingToken());
        } catch (io.jsonwebtoken.JwtException e) {
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
        }

        io.jsonwebtoken.Claims claims = parsed.getBody();
        if (!SCOPE_2FA_PENDING.equals(claims.get("scope", String.class))) {
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
        }

        String email = claims.getSubject();
        var user = repository.findByEmail(email)
                .orElseThrow(() -> new ErrorResponseException(HttpStatus.UNAUTHORIZED));

        if (!twoFactorService.verifyForLogin(user.getId(), request.code())) {
            throw new ErrorResponseException(HttpStatus.UNAUTHORIZED);
        }

        List<String> orgCodes = user.getOrganizations().stream()
                .map(UserOrganization::getOrganizationCode)
                .collect(Collectors.toList());

        boolean firstAccess = firstAccessTokenService.existsByUserIdAndUsedAtIsNull(user.getId());
        String token = buildFullToken(user, orgCodes);
        log.info("2FA verified and full token issued for user {}", user.getId());
        return new UserDTO.LoginResponse(
                user.getId(), orgCodes, user.getEmail(), user.getName(),
                user.getRole(), user.getStatus(),
                token, "Bearer", firstAccess, false, null);
    }

    private String buildFullToken(User user, List<String> orgCodes) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("uid", user.getId());
        claims.put("orgs", orgCodes);
        if (!orgCodes.isEmpty()) {
            claims.put("org", orgCodes.getFirst());
        }
        claims.put("role", user.getRole().name());
        return jwtService.generate(user.getEmail(), claims);
    }

    public String issueRefreshedToken(Long userId) {
        var user = repository.findByIdFetchOrganization(userId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, userId)));
        List<String> orgCodes = user.getOrganizations().stream()
                .map(UserOrganization::getOrganizationCode)
                .collect(Collectors.toList());
        return buildFullToken(user, orgCodes);
    }

    // EPIC-014/TASK-118: vincular uma organização à conta de um usuário (fora do onboarding, ex.:
    // criar 2ª/3ª organização) agora provisiona automaticamente o item ORGANIZATION na
    // BillingSubscription do usuário, herdando o plano já contratado — o frontend não precisa
    // mais fazer uma chamada explícita de "assinatura" para isso.
    @Transactional
    public void addOrganization(Long userId, String orgCode) {
        log.info("Adding organization {} to user {}", orgCode, userId);
        var user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, userId)));

        if (user.getOrganizations().stream().anyMatch(uo -> uo.getOrganizationCode().equals(orgCode))) {
            return;
        }

        validateOrgLimit(userId);

        saveUserOrganization(orgCode, user);

        provisionOrganizationBilling(userId, orgCode);
    }

    private void provisionOrganizationBilling(Long userId, String orgCode) {
        var userItemOpt = billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.USER, userId.toString());

        if (userItemOpt.isEmpty()) {
            log.warn("[Billing] Usuário {} não possui item USER — organização {} vinculada sem provisionar billing", userId, orgCode);
            return;
        }

        boolean alreadyProvisioned = billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, orgCode)
                .isPresent();

        if (alreadyProvisioned) {
            return;
        }

        var userItem = userItemOpt.get();
        billingSubscriptionService.addItem(userItem.getBillingSubscription(),
                BillingSubscriptionItemSourceType.ORGANIZATION, orgCode, userItem.getPlan());
    }

    @Transactional
    public void addOrganizationByOnboarding(Long userId, String orgCode) {
        log.info("[ONBOARDING] - Adding organization {} to user {}", orgCode, userId);
        var user = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, userId)));

        if (user.getOrganizations().stream().anyMatch(uo -> uo.getOrganizationCode().equals(orgCode))) {
            return;
        }

        saveUserOrganization(orgCode, user);
    }

    private void saveUserOrganization(String orgCode, User user) {
        UserOrganization uo = UserOrganization.builder()
                .user(user)
                .organizationCode(orgCode)
                .build();
        user.getOrganizations().add(uo);
        repository.save(user);
    }

    private void validateOrgLimit(Long userId) {
        var subscriptionItemOpt = billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.USER, userId.toString());

        if (subscriptionItemOpt.isEmpty()) {
            log.warn("[OrgLimit] Nenhuma assinatura encontrada para usuário {}", userId);
            throw new RuleException("Usuário não possui uma assinatura ativa.");
        }

        int maxOrganizations = billingPlanFeaturesHelper
                .parse(subscriptionItemOpt.get().getPlan()).getMaxOrganizations();

        if (maxOrganizations <= 0) {
            return; // 0 = ilimitado
        }

        long currentOrgs = userOrganizationRepository.countByUserId(userId);

        if (currentOrgs >= maxOrganizations) {
            throw new RuleException(String.format(
                    "Limite de organizações atingido (%d/%d). Faça upgrade do seu plano para adicionar mais organizações.",
                    currentOrgs, maxOrganizations));
        }
    }

    public void validateUserLimit(Long ownerId, String orgCode) {
        var subscriptionItemOpt = billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.USER, ownerId.toString());

        if (subscriptionItemOpt.isEmpty()) {
            log.warn("[UserLimit] Nenhuma assinatura encontrada para usuário {}", ownerId);
            throw new RuleException("Usuário não possui uma assinatura ativa.");
        }

        int maxUsers = billingPlanFeaturesHelper
                .parse(subscriptionItemOpt.get().getPlan()).getMaxUsers();

        if (maxUsers <= 0) {
            return; // 0 = ilimitado
        }

        long currentUsers = userOrganizationRepository.countByOrganizationCode(orgCode);

        if (currentUsers >= maxUsers) {
            throw new RuleException(String.format(
                    "Limite de usuários atingido (%d/%d). Faça upgrade do seu plano para adicionar mais membros.",
                    currentUsers, maxUsers));
        }
    }

    @Transactional
    public void addOrganizationByInvite(Long memberId, String orgCode) {
        log.info("[TeamMember] Linking organization {} to member {}", orgCode, memberId);
        var user = repository.findById(memberId)
                .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND_MESSAGE, memberId)));

        if (user.getOrganizations().stream().anyMatch(uo -> uo.getOrganizationCode().equals(orgCode))) {
            return;
        }

        saveUserOrganization(orgCode, user);
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

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new NotFoundException("A nova senha não pode ser igual à senha atual");
        }

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
        if (hasLength(request.phoneNumber())) {
            user.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
        }
        if (nonNull(request.whatsappOptIn())) {
            applyWhatsappOptIn(user, request.whatsappOptIn());
        }
        user.setUpdatedAt(Instant.now());

        var updatedUser = repository.save(user);

        return IUserMapper.INSTANCE.toUserResponse(updatedUser);
    }

    private String normalizePhoneNumber(String rawPhoneNumber) {
        return PhoneNumberNormalizer.toE164BR(rawPhoneNumber)
                .orElseThrow(() -> new RuleException(
                        "Telefone inválido. Informe um número de celular ou fixo válido do Brasil."));
    }

    // TASK-122: WhatsApp exige opt-in verificável antes de qualquer envio (política da Meta) —
    // não faz sentido ativar sem um telefone já cadastrado (nem no próprio request, nem já salvo).
    private void applyWhatsappOptIn(User user, boolean whatsappOptIn) {
        if (whatsappOptIn && !hasLength(user.getPhoneNumber())) {
            throw new RuleException(
                    "É necessário cadastrar um telefone antes de ativar notificações por WhatsApp.");
        }
        user.setWhatsappOptIn(whatsappOptIn);
    }

}
