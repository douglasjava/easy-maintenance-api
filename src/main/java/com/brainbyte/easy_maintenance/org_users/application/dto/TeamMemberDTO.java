package com.brainbyte.easy_maintenance.org_users.application.dto;

import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class TeamMemberDTO {

    public record InviteRequest(
            @Schema(example = "joao@empresa.com")
            @Email @NotBlank String email,
            @Schema(example = "João Silva")
            @NotBlank String name,
            @NotNull Role role,
            @NotNull @Size(min = 1, message = "Selecione ao menos uma organização") List<String> orgCodes
    ) {
    }

    public record UpdateRequest(
            @Schema(example = "João Silva Atualizado")
            @NotBlank String name,
            @NotNull Role role,
            @NotNull @Size(min = 1, message = "Selecione ao menos uma organização") List<String> orgCodes
    ) {
    }

    public record OrgInfo(
            String code,
            String name
    ) {
    }

    public record MemberResponse(
            Long id,
            String email,
            String name,
            Role role,
            Status status,
            List<OrgInfo> organizations
    ) {
    }

}
