package com.brainbyte.easy_maintenance.billing.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_subscription_items")
public class BillingSubscriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_subscription_id", nullable = false)
    private BillingSubscription billingSubscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private BillingSubscriptionItemSourceType sourceType;

    @Column(name = "source_id", nullable = false, length = 120)
    private String sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_code", referencedColumnName = "code", nullable = false)
    private BillingPlan plan;

    @Column(name = "value_cents", nullable = false)
    @Builder.Default
    private Long valueCents = 0L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_plan_code", referencedColumnName = "code")
    private BillingPlan nextPlan;

    @Column(name = "plan_change_effective_at")
    private Instant planChangeEffectiveAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

}