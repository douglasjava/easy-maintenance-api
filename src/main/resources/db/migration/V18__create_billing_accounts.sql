CREATE TABLE IF NOT EXISTS billing_accounts
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,

    billing_email VARCHAR(160) NULL,
    doc           VARCHAR(40)  NULL,

    -- Endereço de cobrança (opcional)
    street        VARCHAR(160) NULL,
    number        VARCHAR(20)  NULL,
    complement    VARCHAR(80)  NULL,
    neighborhood  VARCHAR(120) NULL,
    city          VARCHAR(120) NULL,
    state         CHAR(2)      NULL,
    zip_code      VARCHAR(10)  NULL,
    country       VARCHAR(60)  NULL     DEFAULT 'BR',

    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|INACTIVE
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_accounts_user (user_id),
    KEY           idx_billing_accounts_status(status),

    CONSTRAINT fk_billing_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
