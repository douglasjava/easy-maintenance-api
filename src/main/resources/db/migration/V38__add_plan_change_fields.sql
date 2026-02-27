ALTER TABLE user_subscriptions
    ADD COLUMN next_plan_code VARCHAR(40) NULL,
    ADD COLUMN plan_change_effective_at TIMESTAMP NULL;

ALTER TABLE user_subscriptions
    ADD CONSTRAINT fk_user_subscriptions_next_plan
        FOREIGN KEY (next_plan_code) REFERENCES billing_plans (code)
            ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE organization_subscriptions
    ADD COLUMN next_plan_code VARCHAR(40) NULL,
    ADD COLUMN plan_change_effective_at TIMESTAMP NULL;

ALTER TABLE organization_subscriptions
    ADD CONSTRAINT fk_org_subscriptions_next_plan
        FOREIGN KEY (next_plan_code) REFERENCES billing_plans (code)
            ON DELETE RESTRICT ON UPDATE CASCADE;
