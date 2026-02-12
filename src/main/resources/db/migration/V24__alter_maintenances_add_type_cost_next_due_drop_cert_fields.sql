-- 1) Drop indexes que dependem das colunas antigas (se existirem)
DROP INDEX idx_maint_cert ON maintenances;

-- 2) Remover colunas antigas que não farão mais parte do modelo
ALTER TABLE maintenances
    DROP COLUMN issued_by,
    DROP COLUMN certificate_number,
    DROP COLUMN certificate_valid_until,
    DROP COLUMN receipt_url;

-- 3) Adicionar novos campos
-- type: ENUM com default para não quebrar registros existentes
-- performed_by: quem executou
-- cost_cents: custo em centavos
-- next_due_at: próximo vencimento (inicialmente com default para não quebrar)
ALTER TABLE maintenances
    ADD COLUMN `type` ENUM('PREVENTIVA','CORRETIVA','INSPECAO','TESTE','EMERGENCIAL')
    NOT NULL DEFAULT 'INSPECAO' AFTER performed_at,
    ADD COLUMN performed_by VARCHAR(160) DEFAULT NULL AFTER `type`,
    ADD COLUMN cost_cents INT DEFAULT NULL AFTER performed_by,
    ADD COLUMN next_due_at DATE NOT NULL DEFAULT '1970-01-01' AFTER cost_cents;

-- 4) Backfill do next_due_at para dados existentes
-- Regra simples: se não existia antes, usa performed_at como base
UPDATE maintenances
SET next_due_at = performed_at
WHERE next_due_at = '1970-01-01' OR next_due_at IS NULL;

-- 5) Remover default "placeholder" do next_due_at
ALTER TABLE maintenances
    ALTER COLUMN next_due_at DROP DEFAULT;

ALTER TABLE maintenances
    ADD UNIQUE KEY uq_maint_item_date (item_id, performed_at);