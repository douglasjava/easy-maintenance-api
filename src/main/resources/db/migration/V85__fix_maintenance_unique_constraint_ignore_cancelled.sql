-- BUGFIX (achado no QA manual, TASK-QA-MAN-011 C1): a constraint uq_maint_item_date (V24) não
-- considera deleted_at. Soft-delete não remove a linha fisicamente, então cancelar uma manutenção
-- e tentar registrar uma nova para o mesmo item/dia falhava com "Duplicate entry" no MySQL, mesmo
-- a checagem de aplicação (existsByItemIdAndPerformedAt, que respeita @SQLRestriction) já tratando
-- a cancelada como inexistente.
--
-- MySQL não suporta unique index parcial/filtrado nativamente. A solução padrão via coluna gerada
-- (GENERATED ALWAYS AS (IF(deleted_at IS NULL, 0, id))) não é permitida pelo MySQL — ele proíbe
-- coluna gerada referenciar coluna AUTO_INCREMENT (erro 3109). Por isso usamos uma coluna comum
-- (não gerada): todas as manutenções ATIVAS compartilham o valor 0 (preservando a unicidade
-- (item_id, performed_at) entre elas); ao cancelar, MaintenanceService.cancel() seta essa coluna
-- para o próprio id (sempre único), então uma manutenção cancelada nunca conflita com uma nova
-- ativa no mesmo item/dia, nem com outra cancelada anteriormente.
ALTER TABLE maintenances
    DROP INDEX uq_maint_item_date;

ALTER TABLE maintenances
    ADD COLUMN active_dedup_key BIGINT NOT NULL DEFAULT 0;

-- backfill: manutenções já canceladas antes desta migration usam o próprio id, senão colidiriam
-- (todas com active_dedup_key = 0) contra qualquer registro futuro pro mesmo item/dia
UPDATE maintenances
SET active_dedup_key = id
WHERE deleted_at IS NOT NULL;

ALTER TABLE maintenances
    ADD UNIQUE KEY uq_maint_item_date_active (item_id, performed_at, active_dedup_key);
