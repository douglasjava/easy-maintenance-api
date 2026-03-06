package com.brainbyte.easy_maintenance.onboarding.application.dto;

import com.brainbyte.easy_maintenance.ai.application.dto.CompanyType;
import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.commons.validation.Doc;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class OnboardingDTO {

    public record AccountUserRequest(
            @NotEmpty String name,
            @Email String billingEmail,
            @NotNull PaymentMethodType paymentMethod,
            @NotBlank String planCode,
            String doc,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            String country,
            String phone,
            BillingStatus status,
            SubscriptionStatus subscriptionStatus,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialEndsAt
    ) {}

    public record AccountUserResponse(
            Long billingAccountId,
            Long userSubscriptionId
    ){}


    public record AccountOrganizationRequest(
            @Schema(example = "ORG001")
            @NotBlank String code,
            @Schema(example = "Minha Organização")
            @NotBlank String name,
            @Schema(example = "São Paulo") String city,
            @Schema(example = "Av Faria Lima") String street,
            @Schema(example = "75") String number,
            @Schema(example = "32141012") @NotBlank String zipCode,
            @Schema(example = "São Paulo") String state,
            @Schema(example = "Esquina com Rua x") String complement,
            @Schema(example = "Mangabeiras") String neighborhood,
            @Schema(example = "Brasil") String country,
            @Schema(example = "12.345.678/0001-90") String doc,
            @NotNull CompanyType companyType,
            @NotBlank String planCode,
            @NotNull SubscriptionStatus status,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialEndsAt
    ){}

    public record AccountOrganizationResponse(
            Long idOrganization,
            Long idSubscriptionOrganization,
            String codeOrganization,
            String nameOrganization
    ){}

}
