-- Remove o campo location_json da tabela maintenance_items

ALTER TABLE maintenance_items
    DROP COLUMN location_json;
