package com.brainbyte.easy_maintenance.push.domain;

import com.brainbyte.easy_maintenance.org_users.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Entidade de controle de tokens FCM registrados pelos clientes.
 * Importante:
 * - O token pode existir sem vínculo a usuário (usuário ainda não logado)
 * - Após o login, o token existente pode ser associado ao usuário
 * - Um usuário pode ter múltiplos tokens (múltiplos dispositivos/navegadores)
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_push_tokens", indexes = {
        @Index(name = "idx_push_user", columnList = "user_id"),
        @Index(name = "idx_push_user_active", columnList = "user_id,is_active")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uq_push_token", columnNames = {"token"})
})
public class UserPushToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "token", length = 512, nullable = false)
    private String token;

    @Column(name = "platform", length = 20, nullable = false)
    private String platform;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "endpoint", length = 600)
    private String endpoint;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (platform == null) platform = "WEB";
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
