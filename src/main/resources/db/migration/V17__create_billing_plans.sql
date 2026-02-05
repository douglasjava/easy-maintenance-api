CREATE TABLE IF NOT EXISTS billing_plans
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    code          VARCHAR(40)  NOT NULL,                   -- FREE, BUSINESS, PRO, etc
    name          VARCHAR(120) NOT NULL,
    currency      CHAR(3)      NOT NULL DEFAULT 'BRL',
    billing_cycle VARCHAR(20)  NOT NULL DEFAULT 'MONTHLY', -- MONTHLY|YEARLY
    price_cents   INT          NOT NULL DEFAULT 0,
    features_json JSON         NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE|INACTIVE
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_billing_plans_code (code),
    KEY           idx_billing_plans_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
