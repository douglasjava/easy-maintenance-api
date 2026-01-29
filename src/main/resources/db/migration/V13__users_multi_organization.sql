/* 1) Criar tabela de relacionamento user <-> organization */
CREATE TABLE IF NOT EXISTS user_organizations (
                                                  id BIGINT PRIMARY KEY AUTO_INCREMENT,

                                                  user_id BIGINT NOT NULL,
                                                  organization_code CHAR(36) NOT NULL,

                                                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                                  UNIQUE KEY uq_user_org (user_id, organization_code),

                                                  KEY idx_user_org_user (user_id),
                                                  KEY idx_user_org_org (organization_code),

                                                  CONSTRAINT fk_user_org_user
                                                      FOREIGN KEY (user_id) REFERENCES users (id)
                                                          ON DELETE CASCADE ON UPDATE CASCADE,

                                                  CONSTRAINT fk_user_org_org
                                                      FOREIGN KEY (organization_code) REFERENCES organizations (code)
                                                          ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


/* 2) Migrar os vínculos atuais: users.organization_code -> user_organizations */
INSERT INTO user_organizations (user_id, organization_code)
SELECT u.id, u.organization_code
FROM users u
         LEFT JOIN user_organizations uo
                   ON uo.user_id = u.id AND uo.organization_code = u.organization_code
WHERE uo.id IS NULL;


/* 3) Remover FK/índice e coluna organization_code de users */

/* Remover FK antiga (nome conforme seu DDL: fk_users_org) */
ALTER TABLE users DROP FOREIGN KEY fk_users_org;

/* Remover índice antigo (nome conforme seu DDL: idx_users_org) */
ALTER TABLE users DROP INDEX idx_users_org;

/* Remover coluna */
ALTER TABLE users DROP COLUMN organization_code;
