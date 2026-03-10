CREATE TABLE billing_subscriptions (
                                       id BIGINT NOT NULL AUTO_INCREMENT,

                                       billing_account_id BIGINT NOT NULL,

                                       status VARCHAR(20) NOT NULL,

                                       cycle VARCHAR(20) NOT NULL,

                                       external_subscription_id VARCHAR(250),

                                       next_due_date DATE,

                                       current_period_start TIMESTAMP NULL,

                                       current_period_end TIMESTAMP NULL,

                                       next_plan_user_code VARCHAR(100),

                                       next_plan_org_code VARCHAR(100),

                                       plan_change_effective_at TIMESTAMP NULL,

                                       total_cents BIGINT,

                                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                       PRIMARY KEY (id),

                                       UNIQUE KEY uk_bsub_ext_sub (external_subscription_id),
                                       KEY idx_bsub_account (billing_account_id),
                                       KEY idx_bsub_status (status),
                                       KEY idx_bsub_next_due (next_due_date),

                                       CONSTRAINT fk_bsub_account
                                           FOREIGN KEY (billing_account_id)
                                               REFERENCES billing_accounts (id)
                                               ON DELETE RESTRICT ON UPDATE CASCADE

)
  ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci;