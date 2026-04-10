CREATE TABLE in_app_notifications (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    org_code    VARCHAR(100),
    title       VARCHAR(255) NOT NULL,
    body        VARCHAR(500),
    type        VARCHAR(50)  NOT NULL,
    reference_id BIGINT,
    read_at     TIMESTAMP    NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ian_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_ian_user_read ON in_app_notifications(user_id, read_at);
