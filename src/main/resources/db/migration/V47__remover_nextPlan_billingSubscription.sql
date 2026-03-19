ALTER TABLE billing_subscriptions
    DROP COLUMN next_plan_user_code;

ALTER TABLE billing_subscriptions
    DROP COLUMN next_plan_org_code;

ALTER TABLE billing_subscriptions
    DROP COLUMN plan_change_effective_at;