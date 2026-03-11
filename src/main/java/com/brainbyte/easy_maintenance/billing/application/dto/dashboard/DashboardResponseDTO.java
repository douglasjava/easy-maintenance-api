package com.brainbyte.easy_maintenance.billing.application.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponseDTO {
    private DashboardAccountDTO account;
    private DashboardSummaryDTO summary;
    private DashboardPaymentMethodDTO paymentMethod;
    private List<DashboardSubscriptionDTO> subscriptions;
    private DashboardInvoiceDTO nextInvoice;
    private List<DashboardRecentPaymentDTO> recentPayments;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardAccountDTO {
        private String status;
        private String billingEmail;
        private String name;
        private String document;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardSummaryDTO {
        private Long totalMonthlyCents;
        private String currency;
        private LocalDate nextDueDate;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardPaymentMethodDTO {
        private String type;
        private String brand;
        private String last4;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardSubscriptionDTO {
        private String type;
        private String sourceId;
        private String name;
        private String planCode;
        private String planName;
        private Long valueCents;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardInvoiceDTO {
        private Long invoiceId;
        private LocalDate dueDate;
        private Integer totalCents;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardRecentPaymentDTO {
        private Long invoiceId;
        private Integer amountCents;
        private String status;
        private LocalDate paidAt;
    }
}
