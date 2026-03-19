package com.brainbyte.easy_maintenance.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_gateway_events")
public class PaymentGatewayEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String gateway;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "JSON")
    private String payloadJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
