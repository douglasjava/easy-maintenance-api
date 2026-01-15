CREATE TABLE first_access_tokens (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                             user_id BIGINT NOT NULL,
                             token VARCHAR(64) NOT NULL UNIQUE,
                             expires_at TIMESTAMP NOT NULL,
                             used_at TIMESTAMP NULL,
                             created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_fat_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_fat_token ON first_access_tokens(token);
