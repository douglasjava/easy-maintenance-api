-- V71: Compliance e governança do catálogo de normas (TASK-088)
--
-- 1. Adiciona colunas source e pending_review
-- 2. Marca norms AI_BOOTSTRAP como AI_GENERATED + pendente de revisão
-- 3. Corrige norms V9 com period_qty = 0 (itens sem prazo agendado)
-- 4. Beta cleanup: reassocia items que apontam para AI_BOOTSTRAP quando existe norm curada
-- 5. Remove norms AI_GENERATED órfãs (sem items referenciando)

-- ────────────────────────────────────────────────────────────────────────────
-- 1. Schema
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE norms
    ADD COLUMN source        VARCHAR(30)  NOT NULL DEFAULT 'CURATED',
    ADD COLUMN pending_review BOOLEAN     NOT NULL DEFAULT FALSE;

-- ────────────────────────────────────────────────────────────────────────────
-- 2. Marcar norms geradas por IA
-- ────────────────────────────────────────────────────────────────────────────
UPDATE norms
SET source        = 'AI_GENERATED',
    pending_review = TRUE
WHERE authority = 'AI_BOOTSTRAP';

-- ────────────────────────────────────────────────────────────────────────────
-- 3. Corrigir period_qty = 0 nos seeds V9 (norms curadas sem periodicidade)
--    Referências: NR-10 item 10.8 (2a), NR-35 item 35.4 (2a), NR-13 (1a),
--    CBMMG IT 16/17/13 (1a), NR-23 (1a)
-- ────────────────────────────────────────────────────────────────────────────
UPDATE norms SET period_qty = 2, tolerance_days = 30
WHERE item_type = 'NR10_TREINAMENTO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 2, tolerance_days = 30
WHERE item_type = 'NR35_TREINAMENTO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'NR13_CALDEIRAS_INSPECAO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'NR23_PROTECAO_INCENDIO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'SPRINKLERS_SISTEMA' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'ALARME_INCENDIO_SISTEMA' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'HIDRANTES_MANGOTINHOS_SISTEMA' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'HIDRANTES_MANGUEIRAS_INSPECAO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'BOMBAS_INCENDIO_SISTEMA' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'EXTINTORES_SISTEMA_PROTECAO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'SAIDAS_EMERGENCIA_ROTAS' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'SINALIZACAO_EMERGENCIA' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'PORTAS_CORTA_FOGO' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'ILUMINACAO_EMERGENCIA_SISTEMA' AND source = 'CURATED' AND period_qty = 0;

UPDATE norms SET period_qty = 1, tolerance_days = 30
WHERE item_type = 'ILUMINACAO_EMERGENCIA_BATERIAS' AND source = 'CURATED' AND period_qty = 0;

-- ────────────────────────────────────────────────────────────────────────────
-- 4. Beta cleanup: reassociar items que apontam para norm AI_GENERATED
--    quando existe norm curada equivalente para o mesmo item_type
-- ────────────────────────────────────────────────────────────────────────────
UPDATE maintenance_items mi
    INNER JOIN norms ai_n  ON mi.norm_id = ai_n.id AND ai_n.source = 'AI_GENERATED'
    INNER JOIN norms cur   ON cur.item_type = ai_n.item_type AND cur.source = 'CURATED'
SET mi.norm_id = cur.id;

-- ────────────────────────────────────────────────────────────────────────────
-- 5. Remover norms AI_GENERATED que não possuem mais items referenciando
-- ────────────────────────────────────────────────────────────────────────────
DELETE FROM norms
WHERE source = 'AI_GENERATED'
  AND id NOT IN (
      SELECT DISTINCT norm_id FROM maintenance_items WHERE norm_id IS NOT NULL
  );
