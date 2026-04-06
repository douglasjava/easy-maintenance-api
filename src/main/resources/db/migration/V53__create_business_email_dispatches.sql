CREATE TABLE business_email_dispatches (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_code VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    reference_type VARCHAR(100) NOT NULL,
    reference_id BIGINT NOT NULL,
    recipient_email VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    sent_at DATETIME(6),
    created_at DATETIME(6) NOT NULL
);
