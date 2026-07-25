ALTER TABLE business_whatsapp_dispatches
    ADD COLUMN reference_label VARCHAR(255) NULL,
    ADD COLUMN email_already_covered BOOLEAN NOT NULL DEFAULT FALSE;
