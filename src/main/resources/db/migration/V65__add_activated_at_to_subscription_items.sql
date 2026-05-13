ALTER TABLE billing_subscription_items
    ADD COLUMN activated_at TIMESTAMP NULL;

-- backfill: existing items are considered activated when they were created
UPDATE billing_subscription_items
SET activated_at = created_at
WHERE activated_at IS NULL;
