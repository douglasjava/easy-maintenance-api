CREATE TABLE user_subscriptions
(
    id                   BIGINT      NOT NULL AUTO_INCREMENT,

    user_id              BIGINT      NOT NULL,

    plan_code            VARCHAR(40) NOT NULL,

    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | TRIALING | CANCELED | PAST_DUE

    current_period_start DATETIME    NOT NULL,
    current_period_end   DATETIME    NOT NULL,

    trial_ends_at        DATETIME             DEFAULT NULL,

    cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
    canceled_at          DATETIME             DEFAULT NULL,

    created_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    UNIQUE KEY uq_user_subscription (user_id),

    KEY                  idx_user_subscription_plan(plan_code),

    CONSTRAINT fk_user_subscription_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE CASCADE
);
