package com.brainbyte.easy_maintenance.billing.domain;

import com.brainbyte.easy_maintenance.org_users.domain.Organization;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_code", referencedColumnName = "code", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_code", referencedColumnName = "code")
    private BillingPlan plan;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_amount_cents", nullable = false)
    @Builder.Default
    private Integer unitAmountCents = 0;

    @Column(name = "amount_cents", nullable = false)
    @Builder.Default
    private Integer amountCents = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
