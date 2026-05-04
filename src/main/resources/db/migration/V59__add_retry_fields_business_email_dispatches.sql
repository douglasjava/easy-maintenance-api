-- TASK-025: add retry tracking columns to business_email_dispatches.
-- retry_count: how many retry attempts have been made after the initial FAILED.
-- last_retry_at: timestamp of the most recent retry, used to enforce minimum interval between retries.
ALTER TABLE business_email_dispatches
    ADD COLUMN retry_count   TINYINT  NOT NULL DEFAULT 0,
    ADD COLUMN last_retry_at DATETIME NULL;

CREATE INDEX idx_bed_status_retry_created
    ON business_email_dispatches (status, retry_count, created_at);
