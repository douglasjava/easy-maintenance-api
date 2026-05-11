-- Add user_id to ai_jobs to associate each job with its submitting user
ALTER TABLE ai_jobs
    ADD COLUMN user_id BIGINT NULL;
ALTER TABLE ai_jobs
    ADD INDEX idx_ai_jobs_user_id(user_id);

-- Monthly AI credit usage tracking per user (1 credit = 1 completed AI job)
CREATE TABLE ai_usage_monthly
(
    id           BIGINT AUTO_INCREMENT NOT NULL,
    user_id      BIGINT  NOT NULL,
    usage_month  CHAR(7) NOT NULL,
    credits_used INT     NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    UNIQUE INDEX uq_ai_usage_user_month (user_id, usage_month),

    CONSTRAINT fk_ai_usage_user
        FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;