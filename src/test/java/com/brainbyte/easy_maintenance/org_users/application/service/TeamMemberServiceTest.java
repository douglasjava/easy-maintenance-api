package com.brainbyte.easy_maintenance.org_users.application.service;

import com.brainbyte.easy_maintenance.commons.exceptions.ForbiddenException;
import com.brainbyte.easy_maintenance.commons.exceptions.NotFoundException;
import com.brainbyte.easy_maintenance.commons.exceptions.RuleException;
import com.brainbyte.easy_maintenance.infrastructure.mail.utils.EmailTemplateHelper;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserOrganizationRepository userOrganizationRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock UsersService usersService;
    @Mock FirstAccessTokenService firstAccessTokenService;
    @Mock CriticalEmailDispatchService criticalEmailDispatchService;
    @Mock EmailTemplateHelper emailTemplateHelper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks TeamMemberService service;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User adminOwner(Long id) {
        return User.builder().id(id).email("owner@test.com").name("Owner").role(Role.ADMIN).status(Status.ACTIVE).build();
    }

    private User readerMember(Long id) {
        return User.builder().id(id).email("member@test.com").name("Member").role(Role.READER).status(Status.ACTIVE).build();
    }

    private UserOrganization uo(User user, String orgCode) {
        return UserOrganization.builder().user(user).organizationCode(orgCode).build();
    }

    private Organization org(String code, String name) {
        return Organization.builder().code(code).name(name).build();
    }

    private void stubOwnerOrgs(Long ownerId, String... orgCodes) {
        List<UserOrganization> uos = java.util.Arrays.stream(orgCodes)
                .map(code -> uo(adminOwner(ownerId), code))
                .toList();
        when(userOrganizationRepository.findAllByUserId(ownerId)).thenReturn(uos);
    }

    // ── listMembers ───────────────────────────────────────────────────────────

    @Test
    void listMembers_returnsTeamExcludingOwner() {
        User owner = adminOwner(1L);
        User member = readerMember(2L);
        stubOwnerOrgs(1L, "ORGABC");

        when(userOrganizationRepository.findAllByOrganizationCodeInWithUser(List.of("ORGABC")))
                .thenReturn(List.of(uo(owner, "ORGABC"), uo(member, "ORGABC")));
        when(organizationRepository.findAllByCodeIn(List.of("ORGABC")))
                .thenReturn(List.of(org("ORGABC", "Empresa A")));

        List<TeamMemberDTO.MemberResponse> result = service.listMembers(owner);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(2L);
        assertThat(result.getFirst().organizations()).hasSize(1);
        assertThat(result.getFirst().organizations().getFirst().code()).isEqualTo("ORGABC");
    }

    @Test
    void listMembers_throwsForbidden_whenCallerIsNotAdmin() {
        User nonAdmin = User.builder().id(1L).role(Role.READER).build();

        assertThatThrownBy(() -> service.listMembers(nonAdmin))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ADMIN");
    }

    @Test
    void listMembers_returnsEmpty_whenOwnerHasNoOrgs() {
        User owner = adminOwner(1L);
        when(userOrganizationRepository.findAllByUserId(1L)).thenReturn(List.of());

        List<TeamMemberDTO.MemberResponse> result = service.listMembers(owner);

        assertThat(result).isEmpty();
        verifyNoInteractions(organizationRepository);
    }

    // ── inviteMember ─────────────────────────────────────────────────────────

    @Test
    void inviteMember_createsNewUser_andSendsEmail() {
        User owner = adminOwner(1L);
        stubOwnerOrgs(1L, "ORGABC");

        when(userRepository.existsByEmail("joao@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User saved = User.builder().id(10L).email("joao@test.com").name("João").role(Role.READER).status(Status.ACTIVE).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(organizationRepository.findAllByCodeIn(List.of("ORGABC")))
                .thenReturn(List.of(org("ORGABC", "Empresa A")));
        when(emailTemplateHelper.generateAdminInvitationHtml(any(), any(), any(), any()))
                .thenReturn("<html>convite</html>");

        var request = new TeamMemberDTO.InviteRequest("joao@test.com", "João", Role.READER, List.of("ORGABC"));
        TeamMemberDTO.MemberResponse response = service.inviteMember(request, owner);

        assertThat(response.id()).isEqualTo(10L);
        verify(usersService).validateUserLimit(1L, "ORGABC");
        verify(usersService).addOrganizationByInvite(10L, "ORGABC");
        verify(firstAccessTokenService).createForUser(10L);
        verify(criticalEmailDispatchService).send(eq("joao@test.com"), any(), any(), any(), any(), any(), eq(true));
    }

    @Test
    void inviteMember_existingUser_linksWithoutSendingEmail() {
        User owner = adminOwner(1L);
        stubOwnerOrgs(1L, "ORGABC");

        User existing = readerMember(5L);
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);
        when(userRepository.findByEmail("existing@test.com")).thenReturn(Optional.of(existing));
        when(organizationRepository.findAllByCodeIn(List.of("ORGABC")))
                .thenReturn(List.of(org("ORGABC", "Empresa A")));

        var request = new TeamMemberDTO.InviteRequest("existing@test.com", "Existente", Role.READER, List.of("ORGABC"));
        service.inviteMember(request, owner);

        verify(usersService).validateUserLimit(1L, "ORGABC");
        verify(usersService).addOrganizationByInvite(5L, "ORGABC");
        verifyNoInteractions(firstAccessTokenService);
        verifyNoInteractions(criticalEmailDispatchService);
    }

    @Test
    void inviteMember_throwsRuleException_whenUserLimitReached() {
        User owner = adminOwner(1L);
        stubOwnerOrgs(1L, "ORGABC");

        when(userRepository.existsByEmail("joao@test.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User saved = User.builder().id(10L).email("joao@test.com").name("João").role(Role.READER).status(Status.ACTIVE).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);
        doThrow(new RuleException("Limite de usuários atingido"))
                .when(usersService).validateUserLimit(1L, "ORGABC");

        var request = new TeamMemberDTO.InviteRequest("joao@test.com", "João", Role.READER, List.of("ORGABC"));

        assertThatThrownBy(() -> service.inviteMember(request, owner))
                .isInstanceOf(RuleException.class)
                .hasMessageContaining("Limite de usuários atingido");

        verify(usersService, never()).addOrganizationByInvite(any(), any());
    }

    @Test
    void inviteMember_throwsForbidden_whenOrgNotOwnedByOwner() {
        User owner = adminOwner(1L);
        stubOwnerOrgs(1L, "ORGABC");

        var request = new TeamMemberDTO.InviteRequest("joao@test.com", "João", Role.READER, List.of("ORGXXX"));

        assertThatThrownBy(() -> service.inviteMember(request, owner))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("ORGXXX");
    }

    // ── removeMember ─────────────────────────────────────────────────────────

    @Test
    void removeMember_unlinksFromOwnerOrgs() {
        User owner = adminOwner(1L);
        User member = readerMember(2L);
        stubOwnerOrgs(1L, "ORGABC");

        when(userOrganizationRepository.findAllByUserId(2L))
                .thenReturn(List.of(uo(member, "ORGABC")));
        when(userOrganizationRepository.findByUserIdAndOrganizationCode(2L, "ORGABC"))
                .thenReturn(Optional.of(uo(member, "ORGABC")));

        service.removeMember(2L, owner);

        verify(usersService).removeOrganization(2L, "ORGABC");
    }

    @Test
    void removeMember_throwsForbidden_whenOwnerTriesToRemoveSelf() {
        User owner = adminOwner(1L);

        assertThatThrownBy(() -> service.removeMember(1L, owner))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("remover a si mesmo");
    }

    @Test
    void removeMember_throwsNotFound_whenMemberNotInOwnerOrgs() {
        User owner = adminOwner(1L);
        stubOwnerOrgs(1L, "ORGABC");
        when(userOrganizationRepository.findAllByUserId(99L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.removeMember(99L, owner))
                .isInstanceOf(NotFoundException.class);
    }

}
