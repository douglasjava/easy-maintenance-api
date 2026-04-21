-- Integration test seed data for MultiTenantIsolationIT
-- Runs once before the test class via @Sql(executionPhase = BEFORE_TEST_CLASS)

-- Two isolated tenants
INSERT INTO organizations (code, name, plan, created_at, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'Org A - IT', 'FREE', NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222222', 'Org B - IT', 'FREE', NOW(), NOW());

-- Billing plan used by org-a subscription
INSERT INTO billing_plans (code, name, currency, billing_cycle, price_cents, features_json, status, created_at, updated_at)
VALUES ('IT_FREE', 'IT Free Plan', 'BRL', 'MONTHLY', 0,
        '{"maxItems":100,"maxUsers":10,"emailMonthlyLimit":100}', 'ACTIVE', NOW(), NOW());

-- Minimal user required by billing_accounts FK
INSERT INTO users (email, name, role, status, password_hash, created_at, updated_at)
VALUES ('it-owner@brainbyte.test', 'IT Owner', 'ADMIN', 'ACTIVE', '$2a$10$itTestPlaceholderHash', NOW(), NOW());

-- Billing account for test user
INSERT INTO billing_accounts (user_id, billing_email, status, payment_method, name, created_at, updated_at)
SELECT id, 'it-owner@brainbyte.test', 'ACTIVE', 'CARD', 'IT Owner', NOW(), NOW()
FROM users WHERE email = 'it-owner@brainbyte.test';

-- Active subscription linked to billing account
INSERT INTO billing_subscriptions (billing_account_id, status, cycle, current_period_start, current_period_end, total_cents, created_at, updated_at)
SELECT ba.id, 'ACTIVE', 'MONTHLY', NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 0, NOW(), NOW()
FROM billing_accounts ba
         JOIN users u ON ba.user_id = u.id
WHERE u.email = 'it-owner@brainbyte.test';

-- Subscription item: org-a subscribed to IT_FREE (maxItems=100)
INSERT INTO billing_subscription_items (billing_subscription_id, source_type, source_id, plan_code, value_cents, cancel_at_period_end, created_at, updated_at)
SELECT bs.id, 'ORGANIZATION', '11111111-1111-1111-1111-111111111111', 'IT_FREE', 0, FALSE, NOW(), NOW()
FROM billing_subscriptions bs
         JOIN billing_accounts ba ON bs.billing_account_id = ba.id
         JOIN users u ON ba.user_id = u.id
WHERE u.email = 'it-owner@brainbyte.test';

-- Test items for data-isolation assertion:
--   org-a gets 2 items, org-b gets 1 — GET /items for org-a must return exactly the 2
INSERT INTO maintenance_items (organization_code, item_type, item_category, next_due_at, status, created_at, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'IT_ITEM_ORG_A_1', 'OPERATIONAL', DATE_ADD(NOW(), INTERVAL 30 DAY), 'OK', NOW(), NOW()),
    ('11111111-1111-1111-1111-111111111111', 'IT_ITEM_ORG_A_2', 'OPERATIONAL', DATE_ADD(NOW(), INTERVAL 60 DAY), 'OK', NOW(), NOW()),
    ('22222222-2222-2222-2222-222222222222', 'IT_ITEM_ORG_B_1', 'OPERATIONAL', DATE_ADD(NOW(), INTERVAL 30 DAY), 'OK', NOW(), NOW());
