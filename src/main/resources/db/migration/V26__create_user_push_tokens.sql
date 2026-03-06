CREATE TABLE user_push_tokens (
                                  id BIGINT NOT NULL AUTO_INCREMENT,

                                  user_id BIGINT NULL,

    -- token do FCM (pode ser grande)
                                  token VARCHAR(512) NOT NULL,

    -- WEB / ANDROID / IOS (deixa pronto pro futuro)
                                  platform VARCHAR(20) NOT NULL DEFAULT 'WEB',

    -- Permite inativar sem apagar histórico
                                  is_active TINYINT(1) NOT NULL DEFAULT 1,

    -- Opcional: rastrear navegador e origem
                                  endpoint VARCHAR(600) DEFAULT NULL,
                                  device_info VARCHAR(255) DEFAULT NULL,

                                  last_seen_at TIMESTAMP NULL DEFAULT NULL,
                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                  PRIMARY KEY (id),

    -- impede duplicar o mesmo token
                                  UNIQUE KEY uq_push_token (token),

                                  KEY idx_push_user (user_id),
                                  KEY idx_push_user_active (user_id, is_active),

                                  CONSTRAINT fk_push_user
                                      FOREIGN KEY (user_id) REFERENCES users(id)
                                          ON DELETE CASCADE
                                          ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
