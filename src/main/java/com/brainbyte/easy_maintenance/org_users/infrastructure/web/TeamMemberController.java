package com.brainbyte.easy_maintenance.org_users.infrastructure.web;

import com.brainbyte.easy_maintenance.org_users.application.dto.TeamMemberDTO;
import com.brainbyte.easy_maintenance.org_users.application.service.AuthenticationService;
import com.brainbyte.easy_maintenance.org_users.application.service.TeamMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/easy-maintenance/api/v1/me/team/users")
@Tag(name = "Equipe", description = "Gestão de membros da equipe do usuário autenticado")
public class TeamMemberController {

    private final TeamMemberService teamMemberService;
    private final AuthenticationService authenticationService;

    @GetMapping
    @Operation(summary = "Lista todos os membros da equipe do usuário autenticado")
    public List<TeamMemberDTO.MemberResponse> listMembers() {
        var owner = authenticationService.getCurrentUser();
        return teamMemberService.listMembers(owner);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convida um novo membro e vincula às organizações selecionadas")
    public TeamMemberDTO.MemberResponse inviteMember(@Valid @RequestBody TeamMemberDTO.InviteRequest request) {
        var owner = authenticationService.getCurrentUser();
        return teamMemberService.inviteMember(request, owner);
    }

    @PatchMapping("/{memberId}")
    @Operation(summary = "Atualiza dados e organizações vinculadas de um membro")
    public TeamMemberDTO.MemberResponse updateMember(@PathVariable Long memberId,
                                                      @Valid @RequestBody TeamMemberDTO.UpdateRequest request) {
        var owner = authenticationService.getCurrentUser();
        return teamMemberService.updateMember(memberId, request, owner);
    }

    @DeleteMapping("/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove o membro das organizações do usuário autenticado")
    public void removeMember(@PathVariable Long memberId) {
        var owner = authenticationService.getCurrentUser();
        teamMemberService.removeMember(memberId, owner);
    }

}
