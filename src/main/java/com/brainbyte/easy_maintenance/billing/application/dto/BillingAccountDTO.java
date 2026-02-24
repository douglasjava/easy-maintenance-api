package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public class BillingAccountDTO {

    public record BillingAccountResponse(
            Long id,
            Long userId,
            String name,
            String billingEmail,
            PaymentMethodType paymentMethod,
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
            String externalCustomerId,
            BillingStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdateBillingAccountRequest(
            @NotEmpty String name,
            @Email String billingEmail,
            @NotNull PaymentMethodType paymentMethod,
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
            @NotBlank String planCode,
            SubscriptionStatus subscriptionStatus,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant trialEndsAt
    ) {}

}
