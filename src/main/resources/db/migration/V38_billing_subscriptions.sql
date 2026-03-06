CREATE TABLE IF NOT EXISTS billing_subscriptions
(
    id                       BIGINT       NOT NULL AUTO_INCREMENT,

    billing_account_id       BIGINT       NOT NULL,

    external_subscription_id VARCHAR(250) NULL,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | TRIAL | PAST_DUE | CANCELED | EXPIRED

    cycle                    VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY',
    -- MONTHLY | YEARLY

    next_due_date            DATE         NULL,

    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uk_bsub_ext_sub (external_subscription_id),
    KEY idx_bsub_account (billing_account_id),
    KEY idx_bsub_status (status),
    KEY idx_bsub_next_due (next_due_date),

    CONSTRAINT fk_bsub_account
        FOREIGN KEY (billing_account_id)
            REFERENCES billing_accounts (id)
            ON DELETE RESTRICT ON UPDATE CASCADE
)
    ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci;