-- Ajustes gerais para MySQL
-- (opcional) SET sql_mode = 'STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION';

-- ================================
-- oauth2_registered_client
-- ================================
CREATE TABLE IF NOT EXISTS `oauth2_registered_client` (
                                                          `id` VARCHAR(100) NOT NULL,
    `client_id` VARCHAR(100) NOT NULL,
    `client_id_issued_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `client_secret` VARCHAR(200) NULL,
    `client_secret_expires_at` TIMESTAMP NULL,
    `client_name` VARCHAR(200) NOT NULL,
    `client_authentication_methods` VARCHAR(1000) NOT NULL,
    `authorization_grant_types` VARCHAR(1000) NOT NULL,
    `redirect_uris` VARCHAR(1000) NULL,
    `post_logout_redirect_uris` VARCHAR(1000) NULL,
    `scopes` VARCHAR(1000) NOT NULL,
    `client_settings` TEXT NOT NULL,
    `token_settings` TEXT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_oauth2_registered_client_client_id` (`client_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================
-- oauth2_authorization_consent
-- ================================
CREATE TABLE IF NOT EXISTS `oauth2_authorization_consent` (
                                                              `registered_client_id` VARCHAR(100) NOT NULL,
    `principal_name` VARCHAR(200) NOT NULL,
    `authorities` VARCHAR(1000) NOT NULL,
    PRIMARY KEY (`registered_client_id`, `principal_name`),
    CONSTRAINT `fk_consent_registered_client`
    FOREIGN KEY (`registered_client_id`)
    REFERENCES `oauth2_registered_client` (`id`)
    ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================
-- oauth2_authorization
-- ================================
CREATE TABLE IF NOT EXISTS `oauth2_authorization` (
                                                      `id` VARCHAR(100) NOT NULL,
    `registered_client_id` VARCHAR(100) NOT NULL,
    `principal_name` VARCHAR(200) NOT NULL,
    `authorization_grant_type` VARCHAR(100) NOT NULL,
    `authorized_scopes` VARCHAR(1000) NULL,
    `attributes` TEXT NULL,
    `state` VARCHAR(500) NULL,
    `authorization_code_value` MEDIUMTEXT NULL,
    `authorization_code_issued_at` DATETIME(6) NULL,
    `authorization_code_expires_at` DATETIME(6) NULL,
    `authorization_code_metadata` TEXT NULL,
    `access_token_value` MEDIUMTEXT NULL,
    `access_token_issued_at` DATETIME(6) NULL,
    `access_token_expires_at` DATETIME(6) NULL,
    `access_token_metadata` TEXT NULL,
    `access_token_type` VARCHAR(100) NULL,
    `access_token_scopes` VARCHAR(1000) NULL,
    `oidc_id_token_value` MEDIUMTEXT NULL,
    `oidc_id_token_issued_at` DATETIME(6) NULL,
    `oidc_id_token_expires_at` DATETIME(6) NULL,
    `oidc_id_token_metadata` TEXT NULL,
    `refresh_token_value` MEDIUMTEXT NULL,
    `refresh_token_issued_at` DATETIME(6) NULL,
    `refresh_token_expires_at` DATETIME(6) NULL,
    `refresh_token_metadata` TEXT NULL,
    `user_code_value` MEDIUMTEXT NULL,
    `user_code_issued_at` DATETIME(6) NULL,
    `user_code_expires_at` DATETIME(6) NULL,
    `user_code_metadata` TEXT NULL,
    `device_code_value` MEDIUMTEXT NULL,
    `device_code_issued_at` DATETIME(6) NULL,
    `device_code_expires_at` DATETIME(6) NULL,
    `device_code_metadata` TEXT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_auth_registered_client` (`registered_client_id`),
    CONSTRAINT `fk_auth_registered_client`
    FOREIGN KEY (`registered_client_id`)
    REFERENCES `oauth2_registered_client` (`id`)
    ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================
-- SPRING_SESSION
-- ================================
CREATE TABLE IF NOT EXISTS `SPRING_SESSION` (
                                                `PRIMARY_ID` CHAR(36) NOT NULL,
    `SESSION_ID` CHAR(36) NOT NULL,
    `CREATION_TIME` BIGINT NOT NULL,
    `LAST_ACCESS_TIME` BIGINT NOT NULL,
    `MAX_INACTIVE_INTERVAL` INT NOT NULL,
    `EXPIRY_TIME` BIGINT NOT NULL,
    `PRINCIPAL_NAME` VARCHAR(100) NULL,
    PRIMARY KEY (`PRIMARY_ID`),
    UNIQUE KEY `SPRING_SESSION_IX1` (`SESSION_ID`),
    KEY `SPRING_SESSION_IX2` (`EXPIRY_TIME`),
    KEY `SPRING_SESSION_IX3` (`PRINCIPAL_NAME`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================
-- SPRING_SESSION_ATTRIBUTES
-- ================================
CREATE TABLE IF NOT EXISTS `SPRING_SESSION_ATTRIBUTES` (
                                                           `SESSION_PRIMARY_ID` CHAR(36) NOT NULL,
    `ATTRIBUTE_NAME` VARCHAR(200) NOT NULL,
    `ATTRIBUTE_BYTES` BLOB NOT NULL,
    PRIMARY KEY (`SESSION_PRIMARY_ID`, `ATTRIBUTE_NAME`),
    CONSTRAINT `SPRING_SESSION_ATTRIBUTES_FK`
    FOREIGN KEY (`SESSION_PRIMARY_ID`)
    REFERENCES `SPRING_SESSION` (`PRIMARY_ID`)
    ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
