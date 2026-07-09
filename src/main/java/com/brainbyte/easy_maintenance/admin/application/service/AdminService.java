package com.brainbyte.easy_maintenance.admin.application.service;

import com.brainbyte.easy_maintenance.admin.application.dto.AdminMetricsResponse;
import com.brainbyte.easy_maintenance.billing.application.service.BillingSubscriptionService;
import com.brainbyte.easy_maintenance.billing.domain.BillingSubscriptionItemSourceType;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.AccessAdminException;
import com.brainbyte.easy_maintenance.commons.utils.PasswordGenerator;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.FirstAccessTokenService;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.CriticalEmailDispatchService;
import com.brainbyte.easy_maintenance.jobs.service.ExternalCustomerSyncResult;
import com.brainbyte.easy_maintenance.jobs.service.ExternalCustomerSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    @Value("${bootstrap.admin.token}")
    private String adminToken;

    @Value("${frontend.login-url}")
    private String loginUrl;

    private final UsersService usersService;
    private final OrganizationsService organizationsService;
    private final FirstAccessTokenService firstAccessService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;
    private final BillingSubscriptionService billingSubscriptionService;
    private final CriticalEmailDispatchService criticalEmailDispatchService;
    private final EmailTemplateHelper emailTemplateHelper;
    private final ExternalCustomerSyncService externalCustomerSyncService;

    public OrganizationDTO.OrganizationResponse createOrganization(OrganizationDTO.CreateOrganizationRequest request) {

        return organizationsService.create(request);

    }

    public ExternalCustomerSyncResult syncExternalCustomerIds() {
        return externalCustomerSyncService.syncMissingExternalCustomerIds();
    }

    public UserDTO.UserResponse createUser(UserDTO.CreateUserAdminRequest request) {

        var password = PasswordGenerator.generateRandomPassword();
        var createUserRequest = new UserDTO.CreateUserRequest(
                request.email(),
                request.name(),
                request.role(),
                Status.ACTIVE,
                password
        );

        var userResponse = usersService.createUser(createUserRequest);

        if (request.referralCode() != null && !request.referralCode().isBlank()) {
            usersService.applyReferralCode(userResponse.id(), request.referralCode().toUpperCase().trim());
        }

        return initializeUserAccess(userResponse, password, null);

    }

    public UserDTO.UserResponse createUserWithOrganization(UserDTO.CreateUserRequest request, String orgCode) {

        var userResponse = usersService.createUserWithOrganization(request, orgCode);

        return initializeUserAccess(userResponse, request.password(), orgCode);

    }

    public void validateToken(String token) {
        if (token == null || !token.equals(adminToken)) {
            throw new AccessAdminException("Usuário sem permissão de administrador");
        }
    }

    public PageResponse<OrganizationDTO.OrganizationResponse> listAllOrganizations(String name, Plan plan, String city, String doc, Pageable pageable) {
        return organizationsService.listAll(name, plan, city, doc, pageable);
    }

    public OrganizationDTO.OrganizationResponse findByOrganizationId(Long id) {
        return organizationsService.findByIdWithoutBusiness(id);
    }

    public OrganizationDTO.OrganizationResponse updateByOrganizationId(Long id, OrganizationDTO.UpdateOrganizationRequest request) {
        return organizationsService.update(id, request);
    }

    public List<OrganizationDTO.OrganizationWithSubscriptionResponse> listAllOrganizationByIdUser(Long idUser) {
        return organizationsService.listUserOrganizations(idUser);
    }

    public PageResponse<UserDTO.UserResponse> listAllSummary(String name, String email, Pageable pageable) {
        return usersService.listAll(name, email, pageable);
    }

    public UserDTO.UserResponse findByUserId(Long id) {
        return usersService.findById(id);
    }

    public UserDTO.UserResponse updateById(Long id, UserDTO.UpdateUserRequest request) {
        return usersService.updateUser(id, request);
    }

    public AdminMetricsResponse getMetrics() {
        log.info("Collecting admin metrics");
        
        long totalOrganizations = organizationRepository.count();
        long totalUsers = userRepository.count();

        // Agrupa itens de assinatura por plano e conta as ocorrências
        Map<Plan, Long> organizationsByPlan = billingSubscriptionItemRepository.findAll().stream()
                .filter(item -> item.getPlan() != null)
                .collect(Collectors.groupingBy(
                        item -> Plan.from(item.getPlan().getCode()),
                        Collectors.counting()
                ));

        return new AdminMetricsResponse(totalOrganizations, totalUsers, organizationsByPlan);
    }

    public void addOrganizationToUser(Long userId, String orgCode) {
        usersService.addOrganization(userId, orgCode);
    }

    @Transactional
    public void removeOrganizationFromUser(Long userId, String orgCode) {
        usersService.removeOrganization(userId, orgCode);

        billingSubscriptionItemRepository
                .findBySourceTypeAndSourceId(BillingSubscriptionItemSourceType.ORGANIZATION, orgCode)
                .ifPresent(item -> {
                    log.info("Agendando cancelamento de billing da org {} ao desvincular do usuario {}", orgCode, userId);
                    billingSubscriptionService.scheduleItemCancellation(item.getId());
                });
    }

    private UserDTO.UserResponse initializeUserAccess(UserDTO.UserResponse userResponse, String password, String orgCode) {
        var firstAccess = firstAccessService.createForUser(userResponse.id());

        log.info("Usuário criado com sucesso - Token criado para primeiro acesso {}", firstAccess.getToken());

        String subject = "Conclua seu cadastro na Easy Maintenance";
        String htmlContent = emailTemplateHelper.generateAdminInvitationHtml(userResponse.name(), userResponse.email(), password, loginUrl);

        criticalEmailDispatchService.send(
                userResponse.email(),
                userResponse.name(),
                orgCode,
                NotificationEventType.ADMIN_INVITATION,
                subject,
                htmlContent,
                true
        );

        return userResponse;
    }

}
