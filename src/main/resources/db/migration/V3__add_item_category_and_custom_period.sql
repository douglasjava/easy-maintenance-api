-- V3__add_item_category_and_custom_period.sql

ALTER TABLE maintenance_items
  ADD COLUMN item_category VARCHAR(20) NOT NULL DEFAULT 'REGULATORIA',
  ADD COLUMN custom_period_unit VARCHAR(20) NULL,   -- 'DIAS' | 'MESES'
  ADD COLUMN custom_period_qty  INT NULL;

-- Índice auxiliar (consultas por categoria)
CREATE INDEX idx_items_category ON maintenance_items (item_category);

/*
Regras de integridade (aplicadas na aplicação):
- Se item_category = 'REGULATORIA'  -> norm_id NOT NULL, custom_* NULL
- Se item_category = 'OPERACIONAL'  -> norm_id NULL,    custom_* NOT NULL
*/
