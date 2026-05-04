CREATE TABLE ai_jobs (
    id                VARCHAR(36)   NOT NULL,
    organization_code VARCHAR(255)  NOT NULL,
    job_type          VARCHAR(50)   NOT NULL,
    input_json        TEXT          NOT NULL,
    result_json       MEDIUMTEXT    NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    error_message     TEXT          NULL,
    created_at        DATETIME(6)   NOT NULL,
    started_at        DATETIME(6)   NULL,
    completed_at      DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_ai_jobs_org_status   (organization_code, status),
    INDEX idx_ai_jobs_completed_at (completed_at)
) ENGINE = InnoDB;
