CREATE TABLE webhook_dlq
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    provider_event_id VARCHAR(200) NOT NULL,
    event_type        VARCHAR(100) NOT NULL,
    payload           JSON         NOT NULL,
    error_message     TEXT,
    attempts          INT          NOT NULL DEFAULT 0,
    first_failed_at   TIMESTAMP    NOT NULL,
    last_failed_at    TIMESTAMP    NOT NULL,
    replayed_at       TIMESTAMP    NULL,
    PRIMARY KEY (id),
    INDEX idx_webhook_dlq_provider_event_id (provider_event_id),
    INDEX idx_webhook_dlq_replayed_at (replayed_at)
);
