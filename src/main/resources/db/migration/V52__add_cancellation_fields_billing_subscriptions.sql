ALTER TABLE billing_subscriptions
ADD COLUMN cancel_at_period_end BOOLEAN DEFAULT FALSE,
ADD COLUMN canceled_at TIMESTAMP NULL;
