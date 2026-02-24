package com.brainbyte.easy_maintenance.payment.domain;

import com.brainbyte.easy_maintenance.org_users.domain.User;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodStatus;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
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
@Table(name = "payment_methods")
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private PaymentMethodType methodType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Column(name = "external_id", nullable = false, length = 160)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentMethodStatus status = PaymentMethodStatus.ACTIVE;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
