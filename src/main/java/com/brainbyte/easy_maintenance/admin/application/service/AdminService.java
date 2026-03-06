package com.brainbyte.easy_maintenance.admin.application.service;

import com.brainbyte.easy_maintenance.admin.application.dto.AdminMetricsResponse;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionItemRepository;
import com.brainbyte.easy_maintenance.billing.infrastructure.persistence.BillingSubscriptionRepository;
import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.AccessAdminException;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.FirstAccessTokenService;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Plan;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    @Value("${bootstrap.admin.token}")
    private String adminToken;

    private final UsersService usersService;
    private final OrganizationsService organizationsService;
    private final FirstAccessTokenService firstAccessService;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final BillingSubscriptionRepository billingSubscriptionRepository;
    private final BillingSubscriptionItemRepository billingSubscriptionItemRepository;

    public OrganizationDTO.OrganizationResponse createOrganization(OrganizationDTO.CreateOrganizationRequest request) {

        return organizationsService.create(request);

    }

    public UserDTO.UserResponse createUser(UserDTO.CreateUserRequest request) {

        var userResponse = usersService.createUser(request);

        return initializeUserAccess(userResponse);

    }

    public UserDTO.UserResponse createUserWithOrganization(UserDTO.CreateUserRequest request, String orgCode) {

        var userResponse = usersService.createUserWithOrganization(request, orgCode);

        return initializeUserAccess(userResponse);

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
        return organizationsService.findById(id);
    }

    public OrganizationDTO.OrganizationResponse updateByOrganizationId(Long id, OrganizationDTO.UpdateOrganizationRequest request) {
        return organizationsService.update(id, request);
    }

    public List<OrganizationDTO.OrganizationWithSubscriptionResponse> listAllOrganizationByIdUser(Long idUser) {
        return usersService.listUserOrganizations(idUser);
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

    public void removeOrganizationFromUser(Long userId, String orgCode) {
        usersService.removeOrganization(userId, orgCode);
    }

    private UserDTO.UserResponse initializeUserAccess(UserDTO.UserResponse userResponse) {
        var firstAccess = firstAccessService.createForUser(userResponse.id());

        log.info("Usuário criado com sucesso - Token criado para primeiro acesso {}", firstAccess.getToken());

        return userResponse;
    }

}
