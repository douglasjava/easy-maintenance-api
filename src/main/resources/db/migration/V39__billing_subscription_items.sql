CREATE TABLE billing_subscription_items (
                                            id BIGINT NOT NULL AUTO_INCREMENT,

                                            billing_subscription_id BIGINT NOT NULL,

                                            source_type VARCHAR(20) NOT NULL,

                                            source_id VARCHAR(120) NOT NULL,

                                            plan_code VARCHAR(100) NOT NULL,

                                            value_cents BIGINT NOT NULL DEFAULT 0,

                                            next_plan_code VARCHAR(100),

                                            plan_change_effective_at TIMESTAMP NULL,

                                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                            PRIMARY KEY (id),

                                            CONSTRAINT fk_subscription_item_subscription
                                                FOREIGN KEY (billing_subscription_id)
                                                    REFERENCES billing_subscriptions(id)
                                                    ON DELETE CASCADE

)
  ENGINE=InnoDB
DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_0900_ai_ci;