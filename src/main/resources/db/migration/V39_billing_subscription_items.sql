CREATE TABLE IF NOT EXISTS billing_subscription_items
(
    id                         BIGINT      NOT NULL AUTO_INCREMENT,

    billing_subscription_id    BIGINT      NOT NULL,

    source_type                VARCHAR(20) NOT NULL,
    -- USER | ORGANIZATION

    source_id                  VARCHAR(64) NOT NULL,
    -- user_id ou organization_code

    plan_code                  VARCHAR(40) NOT NULL,
    value_cents                BIGINT      NOT NULL DEFAULT 0,

    next_plan_code             VARCHAR(40) NULL,
    plan_change_effective_at   TIMESTAMP   NULL,

    created_at                 TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    KEY idx_bsub_items_bsub (billing_subscription_id),
    KEY idx_bsub_items_source (source_type, source_id),
    KEY idx_bsub_items_plan (plan_code),

    CONSTRAINT fk_bsub_items_bsub
        FOREIGN KEY (billing_subscription_id)
            REFERENCES billing_subscriptions (id)
            ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_bsub_items_plan
        FOREIGN KEY (plan_code)
            REFERENCES billing_plans (code)
            ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_bsub_items_next_plan
        FOREIGN KEY (next_plan_code)
            REFERENCES billing_plans (code)
            ON DELETE RESTRICT ON UPDATE CASCADE
)
    ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci;