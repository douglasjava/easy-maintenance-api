package com.brainbyte.easy_maintenance.org_users.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_totp_settings")
public class UserTotpSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "totp_secret", nullable = false)
    private String totpSecret;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "recovery_token")
    private String recoveryToken;

    @Column(name = "recovery_expires_at")
    private Instant recoveryExpiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
