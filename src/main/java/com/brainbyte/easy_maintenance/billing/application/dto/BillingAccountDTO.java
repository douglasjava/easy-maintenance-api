package com.brainbyte.easy_maintenance.billing.application.dto;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import jakarta.validation.constraints.Email;
import java.time.Instant;

public class BillingAccountDTO {

    public record BillingAccountResponse(
            Long id,
            Long userId,
            String billingEmail,
            String doc,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            String country,
            BillingStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record UpdateBillingAccountRequest(
            @Email String billingEmail,
            String doc,
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            String country,
            BillingStatus status
    ) {}

}
