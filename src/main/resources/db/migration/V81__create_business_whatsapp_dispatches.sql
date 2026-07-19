CREATE TABLE business_whatsapp_dispatches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_code VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    reference_type VARCHAR(100) NOT NULL,
    reference_id BIGINT NOT NULL,
    due_date DATE NOT NULL,
    days_offset INT NOT NULL,
    recipient_phone VARCHAR(20),
    status VARCHAR(50) NOT NULL,
    wamid VARCHAR(255),
    error_message TEXT,
    sent_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_business_whatsapp_dispatches_dedup
        UNIQUE (organization_code, event_type, reference_id, due_date, days_offset)
);

CREATE INDEX idx_bwd_wamid ON business_whatsapp_dispatches (wamid);
