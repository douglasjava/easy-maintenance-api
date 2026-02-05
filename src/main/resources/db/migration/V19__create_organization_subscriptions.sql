CREATE TABLE IF NOT EXISTS organization_subscriptions
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,

    organization_code    CHAR(36)    NOT NULL,
    payer_user_id        BIGINT      NOT NULL,                  -- usuário que paga esta org
    plan_code            VARCHAR(40) NOT NULL,

    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|TRIAL|PAST_DUE|CANCELED
    current_period_start TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    current_period_end   TIMESTAMP   NULL,
    trial_ends_at        TIMESTAMP   NULL,
    cancel_at_period_end TINYINT(1)  NOT NULL DEFAULT 0,
    canceled_at          TIMESTAMP   NULL,

    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- 1 assinatura “corrente” por organização (simples e eficiente)
    UNIQUE KEY uk_org_subscriptions_org (organization_code),

    KEY                  idx_org_subscriptions_payer(payer_user_id),
    KEY                  idx_org_subscriptions_plan(plan_code),
    KEY                  idx_org_subscriptions_status(status),

    CONSTRAINT fk_org_subscriptions_org
        FOREIGN KEY (organization_code) REFERENCES organizations (code)
            ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_org_subscriptions_payer
        FOREIGN KEY (payer_user_id) REFERENCES users (id)
            ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_org_subscriptions_plan
        FOREIGN KEY (plan_code) REFERENCES billing_plans (code)
            ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
