CREATE TABLE payment_gateway_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    gateway VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    external_id VARCHAR(100) NULL,
    payment_id BIGINT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_external_id (external_id),
    INDEX idx_payment_id (payment_id)
);
