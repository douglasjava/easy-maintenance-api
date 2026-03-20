package com.brainbyte.easy_maintenance.billing.domain;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingCycle;
import com.brainbyte.easy_maintenance.billing.domain.enums.SubscriptionStatus;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_subscriptions")
public class BillingSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "billing_account_id", nullable = false)
    private BillingAccount billingAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle", nullable = false, length = 20)
    private BillingCycle cycle;

    @Column(name = "external_subscription_id", length = 250)
    private String externalSubscriptionId;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "total_cents")
    private Long totalCents;

    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @OneToMany(mappedBy = "billingSubscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BillingSubscriptionItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    public void activate(String externalSubscriptionId, LocalDate nextDueDate) {
        this.status = SubscriptionStatus.ACTIVE;
        this.externalSubscriptionId = externalSubscriptionId;
        this.nextDueDate = nextDueDate;
    }

    public void markPendingPayment() {
        this.status = SubscriptionStatus.PENDING_PAYMENT;
    }

    public void markPastDue() {
        this.status = SubscriptionStatus.PAST_DUE;
    }

    public void block() {
        this.status = SubscriptionStatus.BLOCKED;
    }

    public void cancel() {
        this.status = SubscriptionStatus.CANCELED;
    }


    public void addItem(BillingSubscriptionItem item) {
        item.setBillingSubscription(this);
        this.items.add(item);
    }

    public Long calculateTotalCents() {
        return items.stream()
                .map(BillingSubscriptionItem::getValueCents)
                .reduce(0L, Long::sum);
    }

    public static BillingSubscription createTrial(BillingAccount billingAccount) {
        return BillingSubscription.builder()
                .billingAccount(billingAccount)
                .status(SubscriptionStatus.TRIAL)
                .cycle(BillingCycle.MONTHLY)
                .build();
    }

}