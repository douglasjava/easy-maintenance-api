package com.brainbyte.easy_maintenance.infrastructure.notification.domain;

import com.brainbyte.easy_maintenance.infrastructure.notification.enums.InAppNotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "in_app_notifications")
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "org_code", length = 100)
    private String orgCode;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 500)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private InAppNotificationType type;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
