package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.ForbiddenException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.utils.PasswordGenerator;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
import com.brainbyte.easy_maintenance.infrastructure.notification.enums.NotificationEventType;
import com.brainbyte.easy_maintenance.infrastructure.notification.service.CriticalEmailDispatchService;
import com.brainbyte.easy_maintenance.org_users.application.dto.TeamMemberDTO;
import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.org_users.domain.UserOrganization;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.OrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserOrganizationRepository;
import com.brainbyte.easy_maintenance.org_users.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamMemberService {

    @Value("${frontend.login-url:http://localhost:3000/login}")
    private String loginUrl;

    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final UsersService usersService;
    private final FirstAccessTokenService firstAccessTokenService;
    private final CriticalEmailDispatchService criticalEmailDispatchService;
    private final EmailTemplateHelper emailTemplateHelper;
    private final PasswordEncoder passwordEncoder;

    public List<TeamMemberDTO.MemberResponse> listMembers(User owner) {
        requireAdmin(owner);

        List<String> ownerOrgCodes = getOwnerOrgCodes(owner.getId());
        if (ownerOrgCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserOrganization> allLinks = userOrganizationRepository
                .findAllByOrganizationCodeInWithUser(ownerOrgCodes);

        Map<Long, User> memberMap = new LinkedHashMap<>();
        Map<Long, List<String>> memberOrgCodesMap = new LinkedHashMap<>();

        for (UserOrganization uo : allLinks) {
            User member = uo.getUser();
            if (member.getId().equals(owner.getId())) {
                continue; // exclude the owner from their own team list
            }
            memberMap.putIfAbsent(member.getId(), member);
            memberOrgCodesMap.computeIfAbsent(member.getId(), k -> new ArrayList<>())
                    .add(uo.getOrganizationCode());
        }

        Map<String, String> orgNamesByCode = buildOrgNameMap(ownerOrgCodes);

        return memberMap.values().stream()
                .map(member -> toMemberResponse(member, memberOrgCodesMap.get(member.getId()), orgNamesByCode))
                .toList();
    }

    @Transactional
    public TeamMemberDTO.MemberResponse inviteMember(TeamMemberDTO.InviteRequest request, User owner) {
        requireAdmin(owner);

        List<String> ownerOrgCodes = getOwnerOrgCodes(owner.getId());
        validateOrgOwnership(request.orgCodes(), ownerOrgCodes);

        boolean isNewUser = !userRepository.existsByEmail(request.email());

        User member;
        String tempPassword = null;

        if (isNewUser) {
            tempPassword = PasswordGenerator.generateRandomPassword();
            member = User.builder()
                    .email(request.email())
                    .name(request.name())
                    .role(request.role())
                    .status(Status.ACTIVE)
                    .passwordHash(passwordEncoder.encode(tempPassword))
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            member = userRepository.save(member);
            log.info("[TeamMember] New user created: id={}, email={}", member.getId(), member.getEmail());
        } else {
            member = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new NotFoundException("Usuário não encontrado: " + request.email()));
            log.info("[TeamMember] Existing user found: id={}, email={}", member.getId(), member.getEmail());
        }

        for (String orgCode : request.orgCodes()) {
            usersService.validateUserLimit(owner.getId(), orgCode);
            usersService.addOrganizationByInvite(member.getId(), orgCode);
        }

        if (isNewUser) {
            sendInvitationEmail(member, tempPassword);
        }

        Map<String, String> orgNamesByCode = buildOrgNameMap(ownerOrgCodes);
        return toMemberResponse(member, request.orgCodes(), orgNamesByCode);
    }

    @Transactional
    public TeamMemberDTO.MemberResponse updateMember(Long memberId, TeamMemberDTO.UpdateRequest request, User owner) {
        requireAdmin(owner);

        List<String> ownerOrgCodes = getOwnerOrgCodes(owner.getId());
        validateOrgOwnership(request.orgCodes(), ownerOrgCodes);

        User member = userRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Membro não encontrado: " + memberId));

        if (member.getId().equals(owner.getId())) {
            throw new ForbiddenException("Você não pode alterar sua própria configuração de equipe.");
        }

        List<UserOrganization> currentLinks = userOrganizationRepository.findAllByUserId(memberId);
        List<String> currentOrgCodes = currentLinks.stream()
                .map(UserOrganization::getOrganizationCode)
                .toList();

        // add newly selected orgs
        for (String orgCode : request.orgCodes()) {
            if (!currentOrgCodes.contains(orgCode)) {
                usersService.validateUserLimit(owner.getId(), orgCode);
                usersService.addOrganizationByInvite(memberId, orgCode);
            }
        }

        // remove orgs that are in owner's scope but not in new selection
        for (String orgCode : ownerOrgCodes) {
            if (currentOrgCodes.contains(orgCode) && !request.orgCodes().contains(orgCode)) {
                usersService.removeOrganization(memberId, orgCode);
            }
        }

        member.setName(request.name());
        member.setRole(request.role());
        member.setUpdatedAt(Instant.now());
        member = userRepository.save(member);

        Map<String, String> orgNamesByCode = buildOrgNameMap(ownerOrgCodes);
        return toMemberResponse(member, request.orgCodes(), orgNamesByCode);
    }

    @Transactional
    public void removeMember(Long memberId, User owner) {
        requireAdmin(owner);

        if (memberId.equals(owner.getId())) {
            throw new ForbiddenException("Você não pode remover a si mesmo da equipe.");
        }

        List<String> ownerOrgCodes = getOwnerOrgCodes(owner.getId());

        boolean isMemberOfOwnerOrg = userOrganizationRepository
                .findAllByUserId(memberId)
                .stream()
                .anyMatch(uo -> ownerOrgCodes.contains(uo.getOrganizationCode()));

        if (!isMemberOfOwnerOrg) {
            throw new NotFoundException("Membro não encontrado na equipe: " + memberId);
        }

        for (String orgCode : ownerOrgCodes) {
            userOrganizationRepository.findByUserIdAndOrganizationCode(memberId, orgCode)
                    .ifPresent(uo -> usersService.removeOrganization(memberId, orgCode));
        }

        log.info("[TeamMember] Member {} removed from owner {} orgs", memberId, owner.getId());
    }

    private void requireAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Apenas usuários com perfil ADMIN podem gerenciar a equipe.");
        }
    }

    private List<String> getOwnerOrgCodes(Long ownerId) {
        return userOrganizationRepository.findAllByUserId(ownerId)
                .stream()
                .map(UserOrganization::getOrganizationCode)
                .toList();
    }

    private void validateOrgOwnership(List<String> requestedOrgCodes, List<String> ownerOrgCodes) {
        List<String> unauthorized = requestedOrgCodes.stream()
                .filter(code -> !ownerOrgCodes.contains(code))
                .toList();
        if (!unauthorized.isEmpty()) {
            throw new ForbiddenException(
                    "Organizações não pertencem ao seu perfil: " + String.join(", ", unauthorized));
        }
    }

    private Map<String, String> buildOrgNameMap(List<String> orgCodes) {
        return organizationRepository.findAllByCodeIn(orgCodes)
                .stream()
                .collect(Collectors.toMap(Organization::getCode, Organization::getName));
    }

    private TeamMemberDTO.MemberResponse toMemberResponse(User member, List<String> orgCodes,
                                                           Map<String, String> orgNamesByCode) {
        List<TeamMemberDTO.OrgInfo> orgInfos = orgCodes == null ? List.of() : orgCodes.stream()
                .map(code -> new TeamMemberDTO.OrgInfo(code, orgNamesByCode.getOrDefault(code, code)))
                .toList();

        return new TeamMemberDTO.MemberResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getRole(),
                member.getStatus(),
                orgInfos
        );
    }

    private void sendInvitationEmail(User member, String tempPassword) {
        firstAccessTokenService.createForUser(member.getId());
        String subject = "Você foi convidado para a Easy Maintenance";
        String html = emailTemplateHelper.generateAdminInvitationHtml(
                member.getName(), member.getEmail(), tempPassword, loginUrl);
        criticalEmailDispatchService.send(
                member.getEmail(),
                member.getName(),
                null,
                NotificationEventType.ADMIN_INVITATION,
                subject,
                html,
                true
        );
        log.info("[TeamMember] Invitation email sent to {}", member.getEmail());
    }

}
