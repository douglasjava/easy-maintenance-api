ALTER TABLE billing_accounts
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'CARD';