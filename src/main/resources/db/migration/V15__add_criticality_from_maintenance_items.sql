-- Add o campo criticality da tabela maintenance_items

ALTER TABLE maintenance_items
    ADD COLUMN criticality VARCHAR(50) NULL