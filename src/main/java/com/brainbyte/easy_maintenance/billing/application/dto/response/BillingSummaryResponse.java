package com.brainbyte.easy_maintenance.billing.application.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingSummaryResponse {
    private SubscriptionSummaryDTO subscription;
    private List<SubscriptionItemDTO> items;
    private List<InvoiceSummaryDTO> invoices;
    private BillingAccountSummaryDTO billingAccount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionSummaryDTO {
        private Long id;
        private String status;
        private String cycle;
        private Long totalCents;
        private LocalDate nextDueDate;
        private Long projectedTotalCents;
        private LocalDate projectedChangeDate;
        // EPIC-014/TASK-115: uso do plano único por conta (0 = ilimitado)
        private int maxOrganizations;
        private long organizationsUsed;
        private int maxUsers;
        private long usersUsed;
        private int maxItems;
        private long itemsUsedTotalAccount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionItemDTO {
        private Long id;
        private String type;
        private String name;
        private String reference;
        private PlanDTO plan;
        private Long valueCents;
        private PendingChangeDTO pendingChange;
        private boolean cancelAtPeriodEnd;
        private LocalDate scheduledCancellationDate;
        // EPIC-014/TASK-115: itens usados por esta organização dentro do pool da conta (null para o item USER)
        private Long itemsUsedByOrg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanDTO {
        private String code;
        private String name;
        private Long priceCents;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingChangeDTO {
        private PlanDTO nextPlan;
        private Instant effectiveAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvoiceSummaryDTO {
        private Long id;
        private String status;
        private Long amountCents;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String paymentLink;
        private String receiptUrl;
        private Boolean fromPlanChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillingAccountSummaryDTO {
        private String email;
        private String paymentMethod;
        private String cardLast4;
        private String cardBrand;
    }
}
