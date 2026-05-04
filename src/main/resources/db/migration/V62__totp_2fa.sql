-- 2FA TOTP settings per user
CREATE TABLE user_totp_settings (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    totp_secret    VARCHAR(64) NOT NULL,
    enabled        BOOLEAN     NOT NULL DEFAULT FALSE,
    recovery_token VARCHAR(128)         DEFAULT NULL,
    recovery_expires_at TIMESTAMP       DEFAULT NULL,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_totp_user_id (user_id),
    CONSTRAINT fk_user_totp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One-time backup codes for recovery
CREATE TABLE user_backup_codes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    code_hash  VARCHAR(128) NOT NULL,
    used_at    TIMESTAMP             DEFAULT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_backup_codes_user_id (user_id),
    CONSTRAINT fk_backup_codes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Org-level flag: administrators can require 2FA for all members
ALTER TABLE organizations
    ADD COLUMN require_2fa BOOLEAN NOT NULL DEFAULT FALSE;
