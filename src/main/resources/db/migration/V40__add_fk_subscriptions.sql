ALTER TABLE user_subscriptions
    ADD COLUMN billing_subscription_id BIGINT NULL,
    ADD KEY idx_user_sub_bsub (billing_subscription_id),
    ADD CONSTRAINT fk_user_sub_bsub
        FOREIGN KEY (billing_subscription_id)
            REFERENCES billing_subscriptions (id)
            ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE organization_subscriptions
    ADD COLUMN billing_subscription_id BIGINT NULL,
    ADD KEY idx_org_sub_bsub (billing_subscription_id),
    ADD CONSTRAINT fk_org_sub_bsub
        FOREIGN KEY (billing_subscription_id)
            REFERENCES billing_subscriptions (id)
            ON DELETE SET NULL ON UPDATE CASCADE;