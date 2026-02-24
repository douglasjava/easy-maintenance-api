package com.brainbyte.easy_maintenance.payment.domain;

import com.brainbyte.easy_maintenance.billing.domain.Invoice;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentMethodType;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentProvider;
import com.brainbyte.easy_maintenance.payment.domain.enums.PaymentStatus;
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
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payer_user_id", nullable = false)
    private User payer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, length = 20)
    private PaymentMethodType methodType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "amount_cents", nullable = false)
    private Integer amountCents;

    @Column(nullable = false, length = 8)
    @Builder.Default
    private String currency = "BRL";

    @Column(name = "external_payment_id", length = 120)
    private String externalPaymentId;

    @Column(name = "external_reference", length = 120)
    private String externalReference;

    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    @Column(name = "pix_qr_code_base64", columnDefinition = "MEDIUMTEXT")
    private String pixQrCodeBase64;

    @Column(name = "payment_link", length = 600)
    private String paymentLink;

    @Column(name = "failure_reason", length = 240)
    private String failureReason;

    @Column(name = "raw_payload_json", columnDefinition = "JSON")
    private String rawPayloadJson;

    @Column(name = "paid_at")
    private Instant paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
