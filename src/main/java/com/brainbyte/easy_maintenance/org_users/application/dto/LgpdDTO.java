package com.brainbyte.easy_maintenance.org_users.application.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

public class LgpdDTO {

    public record DataExportResponse(
            UserInfo user,
            BillingInfo billing,
            List<String> organizations,
            List<AiUsageInfo> aiUsage,
            String exportedAt
    ) {
        public record UserInfo(
                Long id,
                String name,
                String email,
                String role,
                String status,
                Instant createdAt
        ) {}

        public record BillingInfo(
                String name,
                String billingEmail,
                String doc,
                String phone,
                String street,
                String number,
                String complement,
                String neighborhood,
                String city,
                String state,
                String zipCode,
                String country,
                String paymentMethod
        ) {}

        public record AiUsageInfo(String month, int creditsUsed) {}
    }

    public record AnonymizeAccountRequest(
            @NotBlank(message = "Confirmação de senha é obrigatória")
            String confirmPassword
    ) {}
}
