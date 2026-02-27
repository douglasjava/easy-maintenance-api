package com.brainbyte.easy_maintenance.billing.application.dto.response;

import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MySubscriptionStatusResponse {

    private SubscriptionStatus status;
    private Instant trialEndsAt;
    private boolean isBlocked;
    private LocalDate nextInvoiceDate;
    private String paymentLink;

}
