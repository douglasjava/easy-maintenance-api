package com.brainbyte.easy_maintenance.org_users.application.dto;

import com.brainbyte.easy_maintenance.ai.application.dto.CompanyType;
import com.brainbyte.easy_maintenance.commons.validation.Doc;
import com.brainbyte.easy_maintenance.billing.application.dto.response.BillingSubscriptionResponse;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Role;
import com.brainbyte.easy_maintenance.org_users.domain.enums.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class OrganizationDTO {

    public record CreateOrganizationRequest(
            @Schema(example = "ORG001")
            @NotBlank String code,
            @Schema(example = "Minha Organização")
            @NotBlank String name,
            @Schema(example = "São Paulo") String city,
            @Schema(example = "Av Faria Lima") String street,
            @Schema(example = "75") String number,
            @Schema(example = "32141012") String zipCode,
            @Schema(example = "São Paulo") String state,
            @Schema(example = "Esquina com Rua x") String complement,
            @Schema(example = "Mangabeiras") String neighborhood,
            @Schema(example = "Brasil") String country,
            @Schema(example = "12.345.678/0001-90") @NotBlank @Doc String doc,
            @NotNull CompanyType companyType
    ) {
    }

    public record UpdateOrganizationRequest(
            @Schema(example = "Nome Atualizado") @NotBlank String name,
            @Schema(example = "Rio de Janeiro") String city,
            @Schema(example = "Av Faria Lima") String street,
            @Schema(example = "75") String number,
            @Schema(example = "32141012") String zipCode,
            @Schema(example = "São Paulo") String state,
            @Schema(example = "Esquina com Rua x") String complement,
            @Schema(example = "Mangabeiras") String neighborhood,
            @Schema(example = "Brasil") String country,
            @Schema(example = "12.345.678/0001-90") String doc
    ) {
    }

    public record OrganizationResponse(
            Long id,
            String code,
            String name,
            String city,
            String street,
            String number,
            String complement,
            String neighborhood,
            String state,
            String zipCode,
            String country,
            String doc,
            CompanyType companyType,
            Long billingSubscriptionItem
    ) {}

    public record OrganizationWithSubscriptionResponse(
            OrganizationResponse organization,
            BillingSubscriptionResponse.SubscriptionItemResponse subscription
    ) {}

    public record ResponsibleUser(
            Long id,
            String email,
            String name,
            Role role,
            Status status
    ) {}

}
