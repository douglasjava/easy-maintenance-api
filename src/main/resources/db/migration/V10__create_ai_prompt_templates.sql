CREATE TABLE IF NOT EXISTS ai_prompt_templates (
                                                   id BIGINT PRIMARY KEY AUTO_INCREMENT,

                                                   template_key VARCHAR(80) NOT NULL,
                                                   company_type VARCHAR(40) NOT NULL,

                                                   version INT NOT NULL DEFAULT 1,
                                                   status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

                                                   system_prompt LONGTEXT NOT NULL,
                                                   user_prompt LONGTEXT NOT NULL,

                                                   output_schema_json JSON NULL,

                                                   model_name VARCHAR(80) NULL,
                                                   temperature DECIMAL(3,2) NULL,
                                                   max_tokens INT NULL,

                                                   created_by VARCHAR(120) NULL,
                                                   created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                   updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);


CREATE INDEX idx_ai_prompt_templates_lookup
    ON ai_prompt_templates (template_key, company_type, status);

CREATE INDEX idx_ai_prompt_templates_updated_at
    ON ai_prompt_templates (updated_at);
