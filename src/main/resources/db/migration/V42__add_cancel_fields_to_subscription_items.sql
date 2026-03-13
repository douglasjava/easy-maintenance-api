ALTER TABLE billing_subscription_items
ADD COLUMN cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN canceled_at TIMESTAMP NULL;
