CREATE TABLE landing_leads (
                               id BIGINT NOT NULL AUTO_INCREMENT,

    -- contato (opcional, porque pode ser só visita)
                               email VARCHAR(160) DEFAULT NULL,
                               name VARCHAR(160) DEFAULT NULL,

    -- origem / campanha (opcional)
                               source VARCHAR(60) DEFAULT NULL,      -- ex: "landing", "instagram", "google"
                               medium VARCHAR(60) DEFAULT NULL,      -- ex: "cpc", "social", "organic"
                               campaign VARCHAR(120) DEFAULT NULL,   -- ex: "fev-2026-launch"

    -- rastreio simples
                               referrer VARCHAR(600) DEFAULT NULL,
                               landing_path VARCHAR(300) DEFAULT NULL,  -- ex: "/"
                               utm_json JSON DEFAULT NULL,              -- guarda utm completos se quiser

    -- informações de “quem entrou”
                               ip VARCHAR(45) DEFAULT NULL,             -- IPv4/IPv6
                               user_agent VARCHAR(600) DEFAULT NULL,

    -- status simples
                               status VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW | CONTACTED | IGNORED

                               created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                               PRIMARY KEY (id),

                               KEY idx_landing_leads_created (created_at),
                               KEY idx_landing_leads_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
