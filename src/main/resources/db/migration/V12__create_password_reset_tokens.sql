CREATE TABLE IF NOT EXISTS password_reset_tokens (
                                                     id BIGINT PRIMARY KEY AUTO_INCREMENT,

                                                     user_id BIGINT NOT NULL,

    -- Armazene apenas o HASH do token (ex.: SHA-256 em hex = 64 chars)
                                                     token_hash VARCHAR(64) NOT NULL,

                                                     expires_at TIMESTAMP NOT NULL,
                                                     used_at TIMESTAMP NULL,

                                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Opcional (recomendado para auditoria e segurança)
                                                     requested_ip VARCHAR(45) NULL,
                                                     requested_user_agent VARCHAR(255) NULL,

    -- Um usuário pode ter vários tokens ao longo do tempo,
    -- mas o hash deve ser único no sistema
                                                     CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash),
                                                     CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_user_id ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_expires_at ON password_reset_tokens (expires_at);
