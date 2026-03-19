ALTER TABLE payments
ADD COLUMN receipt_url VARCHAR(500) NULL,
ADD COLUMN invoice_number VARCHAR(50) NULL,
ADD COLUMN net_amount_cents INT NULL,
ADD COLUMN gateway_fee_cents INT NULL,
ADD COLUMN gateway_status VARCHAR(50) NULL;
