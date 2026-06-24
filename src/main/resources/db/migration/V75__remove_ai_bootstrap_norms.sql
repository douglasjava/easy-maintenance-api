-- Remove todas as normas geradas pela IA (authority = 'AI_BOOTSTRAP').
-- Itens regulatórios vinculados a essas normas são convertidos para OPERATIONAL
-- (sem norma associada), alinhado com a nova política: IA nunca gera normas
-- regulatórias — apenas reutiliza normas curadas do catálogo ou cria OPERATIONAL.

-- 1. Desvincula itens que referenciam normas AI_BOOTSTRAP
UPDATE maintenance_items
SET norm_id    = NULL,
    item_category = 'OPERATIONAL'
WHERE norm_id IN (SELECT id FROM norms WHERE authority = 'AI_BOOTSTRAP');

-- 2. Remove as normas AI_BOOTSTRAP
DELETE FROM norms WHERE authority = 'AI_BOOTSTRAP';
