ALTER TABLE billing_accounts
    ADD COLUMN phone VARCHAR(20) NULL,
    ADD COLUMN external_customer_id VARCHAR(250) NULL;

