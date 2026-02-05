package com.brainbyte.easy_maintenance.billing.domain;

import com.brainbyte.easy_maintenance.billing.domain.enums.BillingStatus;
import com.brainbyte.easy_maintenance.org_users.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "billing_accounts")
public class BillingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "billing_email", length = 160)
    private String billingEmail;

    @Column(length = 40)
    private String doc;

    @Column(length = 160)
    private String street;

    @Column(length = 20)
    private String number;

    @Column(length = 80)
    private String complement;

    @Column(length = 120)
    private String neighborhood;

    @Column(length = 120)
    private String city;

    @Column(length = 2)
    private String state;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    @Column(length = 60)
    @Builder.Default
    private String country = "BR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BillingStatus status = BillingStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
