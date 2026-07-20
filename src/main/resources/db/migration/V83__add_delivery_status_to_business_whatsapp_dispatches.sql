ALTER TABLE business_whatsapp_dispatches
    ADD COLUMN delivery_status VARCHAR(20) NULL,
    ADD COLUMN delivered_at DATETIME(6) NULL,
    ADD COLUMN read_at DATETIME(6) NULL,
    ADD COLUMN failed_error_code VARCHAR(20) NULL,
    ADD COLUMN failed_error_message TEXT NULL;
