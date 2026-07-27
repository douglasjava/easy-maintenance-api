-- TASK-137: campos de cancelamento de manutenção (correção sem edição/perda de histórico)
-- Sem FK em cancelled_by, mesmo padrão de created_by/updated_by (V76) — evita join oculto em queries de listagem
ALTER TABLE maintenances
    ADD COLUMN cancelled_at DATETIME(6) NULL,
    ADD COLUMN cancelled_by BIGINT NULL,
    ADD COLUMN cancel_reason TEXT NULL;
