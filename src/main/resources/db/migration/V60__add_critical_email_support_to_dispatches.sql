-- TASK-025: extend business_email_dispatches to support critical transactional emails.
-- organization_code, reference_type and reference_id become nullable because critical emails
-- (password reset, trial expiring, etc.) are not scoped to a specific tenant entity.
-- subject and html_content store pre-rendered content so the retry job can resend without
-- needing to reconstruct complex templates.
-- retryable = 0 excludes time-sensitive emails (e.g. password reset with 30-min token)
-- from the job-based retry while still tracking them for observability.
ALTER TABLE business_email_dispatches
    MODIFY COLUMN organization_code VARCHAR(255) NULL,
    MODIFY COLUMN reference_type    VARCHAR(50)  NULL,
    MODIFY COLUMN reference_id      BIGINT       NULL;

ALTER TABLE business_email_dispatches
    ADD COLUMN subject      VARCHAR(500) NULL,
    ADD COLUMN html_content MEDIUMTEXT   NULL,
    ADD COLUMN retryable    TINYINT(1)   NOT NULL DEFAULT 1;
