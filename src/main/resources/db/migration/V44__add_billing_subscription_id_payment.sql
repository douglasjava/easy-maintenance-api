ALTER TABLE payments
    ADD COLUMN billing_subscription_id BIGINT NULL AFTER invoice_id,

    ADD INDEX idx_payments_billing_subscription (billing_subscription_id),

    ADD CONSTRAINT fk_payments_billing_subscription
        FOREIGN KEY (billing_subscription_id)
        REFERENCES billing_subscriptions (id)
        ON DELETE SET NULL
           ON UPDATE CASCADE;