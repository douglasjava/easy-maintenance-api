-- V78: Corrige periodicidade do SPDA_INSPECAO_COMPLETA e remove duplicidades no catálogo de normas.
--
-- 1. Corrige period_qty de SPDA_INSPECAO_COMPLETA (5 -> 3 anos, NBR 5419-3 item 7.3.2)
-- 2. Resolve pares de item_type duplicados: reassocia maintenance_items do perdedor
--    para o vencedor (norm_id + item_type) antes de remover a norm perdedora
-- 3. Limpa pending_review e marcações "[REVISAR: ...]" das norms vencedoras remanescentes
--
-- Sem DDL nesta migration -> Flyway executa tudo em uma única transação (comportamento padrão).

-- ────────────────────────────────────────────────────────────────────────────
-- 1. SPDA_INSPECAO_COMPLETA: period_qty 5 -> 3
--    NBR 5419-3, item 7.3.2: inspeção completa a cada 3 anos para estruturas
--    de risco comum; 1 ano para estruturas de risco elevado. O catálogo trata
--    apenas o caso comum (risco elevado é tratado como item customizado).
--    WHERE com period_qty = 5 garante idempotência (não reaplica se já corrigido).
-- ────────────────────────────────────────────────────────────────────────────
UPDATE norms
SET period_qty = 3,
    notes = 'Inspeção completa a cada 3 anos para estruturas de risco comum (NBR 5419-3, item 7.3.2). Estruturas de risco elevado (explosivos, inflamáveis, grande concentração de pessoas) exigem inspeção anual.'
WHERE item_type = 'SPDA_INSPECAO_COMPLETA'
  AND period_unit = 'ANUAL'
  AND period_qty = 5;

-- ────────────────────────────────────────────────────────────────────────────
-- 2. Deduplicação de item_type em norms
--    Para cada par (vencedor mantém, perdedor remove):
--      a) reassocia maintenance_items do perdedor -> vencedor (norm_id + item_type)
--      b) remove a norm perdedora
--    Ids resolvidos dinamicamente por item_type (não hardcode).
--    Não altera next_due_at nem last_performed_at dos itens realocados.
-- ────────────────────────────────────────────────────────────────────────────

-- EXTINTORES_SISTEMA_PROTECAO -> EXTINTOR
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id AND loser.item_type = 'EXTINTORES_SISTEMA_PROTECAO'
    INNER JOIN norms winner ON winner.item_type = 'EXTINTOR'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms WHERE item_type = 'EXTINTORES_SISTEMA_PROTECAO';

-- PORTAS_CORTA_FOGO -> PORTA_CORTA_FOGO
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id AND loser.item_type = 'PORTAS_CORTA_FOGO'
    INNER JOIN norms winner ON winner.item_type = 'PORTA_CORTA_FOGO'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms WHERE item_type = 'PORTAS_CORTA_FOGO';

-- HIDRANTES_MANGUEIRAS_INSPECAO -> MANGUEIRA_DE_INCENDIO
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id AND loser.item_type = 'HIDRANTES_MANGUEIRAS_INSPECAO'
    INNER JOIN norms winner ON winner.item_type = 'MANGUEIRA_DE_INCENDIO'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms WHERE item_type = 'HIDRANTES_MANGUEIRAS_INSPECAO';

-- ALARME_INCENDIO_SISTEMA -> ALARME_DE_INCENDIO
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id AND loser.item_type = 'ALARME_INCENDIO_SISTEMA'
    INNER JOIN norms winner ON winner.item_type = 'ALARME_DE_INCENDIO'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms WHERE item_type = 'ALARME_INCENDIO_SISTEMA';

-- ILUMINACAO_EMERGENCIA_SISTEMA -> ILUMINACAO_EMERGENCIA
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id AND loser.item_type = 'ILUMINACAO_EMERGENCIA_SISTEMA'
    INNER JOIN norms winner ON winner.item_type = 'ILUMINACAO_EMERGENCIA'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms WHERE item_type = 'ILUMINACAO_EMERGENCIA_SISTEMA';

-- ILUMINACAO_EMERGENCIA_BATERIAS -> ILUMINACAO_EMERGENCIA
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id AND loser.item_type = 'ILUMINACAO_EMERGENCIA_BATERIAS'
    INNER JOIN norms winner ON winner.item_type = 'ILUMINACAO_EMERGENCIA'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms WHERE item_type = 'ILUMINACAO_EMERGENCIA_BATERIAS';

-- SPDA (MESES/12) -> SPDA_INSPECAO_VISUAL
-- filtro extra period_unit/period_qty isola exatamente a norm duplicada do seed V2,
-- sem colidir com SPDA_INSPECAO_COMPLETA/SPDA_INSPECAO_VISUAL (item_type distintos)
UPDATE maintenance_items mi
    INNER JOIN norms loser  ON mi.norm_id = loser.id
        AND loser.item_type = 'SPDA'
        AND loser.period_unit = 'MESES'
        AND loser.period_qty = 12
    INNER JOIN norms winner ON winner.item_type = 'SPDA_INSPECAO_VISUAL'
SET mi.norm_id   = winner.id,
    mi.item_type = winner.item_type;

DELETE FROM norms
WHERE item_type = 'SPDA'
  AND period_unit = 'MESES'
  AND period_qty = 12;

-- ────────────────────────────────────────────────────────────────────────────
-- 3. Limpeza final: zera pending_review e remove marcações "[REVISAR: ...]"
--    das notes das norms vencedoras remanescentes.
-- ────────────────────────────────────────────────────────────────────────────
UPDATE norms
SET pending_review = 0,
    notes = NULLIF(TRIM(REGEXP_REPLACE(REGEXP_REPLACE(notes, '\\[REVISAR:[^\\]]*\\]', ''), '  +', ' ')), '')
WHERE pending_review = 1
   OR notes REGEXP '\\[REVISAR:';

-- ────────────────────────────────────────────────────────────────────────────
-- 4. Queries de verificação manual (NÃO EXECUTAR como parte da migration)
-- ────────────────────────────────────────────────────────────────────────────

-- Não deve sobrar item_type duplicado em norms:
-- SELECT item_type, COUNT(*) AS total FROM norms GROUP BY item_type HAVING total > 1;

-- Não deve haver maintenance_items ativo com norm_id nulo por causa desta migration:
-- SELECT id, item_type, norm_id FROM maintenance_items WHERE deleted_at IS NULL AND norm_id IS NULL;
