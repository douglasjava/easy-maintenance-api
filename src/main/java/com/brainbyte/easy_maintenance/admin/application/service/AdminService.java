package com.brainbyte.easy_maintenance.admin.application.service;

import com.brainbyte.easy_maintenance.commons.dto.PageResponse;
import com.brainbyte.easy_maintenance.commons.exceptions.AccessAdminException;
import com.brainbyte.easy_maintenance.org_users.application.dto.OrganizationDTO;
import com.brainbyte.easy_maintenance.org_users.application.dto.UserDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.FirstAccessTokenService;
import com.brainbyte.easy_maintenance.org_users.application.service.OrganizationsService;
import com.brainbyte.easy_maintenance.org_users.application.service.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    @Value("${bootstrap.admin.token}")
    private String adminToken;

    public final UsersService usersService;
    public final OrganizationsService organizationsService;
    private final FirstAccessTokenService firstAccessService;

    public OrganizationDTO.OrganizationResponse createOrganization(OrganizationDTO.CreateOrganizationRequest request) {

        return organizationsService.create(request);

    }

    public UserDTO.UserResponse createUser(UserDTO.CreateUserRequest request, String orgCode) {

        var userResponse = usersService.createUser(request, orgCode);

        var firstAccess = firstAccessService.createForUser(userResponse.id());

        log.info("Usuário criado com sucesso - Token criado para primeiro acesso {}", firstAccess.getToken());

        return userResponse;

    }

    public void validateToken(String token) {
        if (token == null || !token.equals(adminToken)) {
            throw new AccessAdminException("Usuário sem permissão de administrador");
        }
    }

    public PageResponse<OrganizationDTO.OrganizationResponse> listAllOrganizations(Pageable pageable) {
        return organizationsService.listAll(pageable);
    }

}
