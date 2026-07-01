-- TASK-103: audit fields for team member traceability
-- Using simple BIGINT (no FK) to avoid hidden N+1 joins on list queries

ALTER TABLE maintenance_items
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;

ALTER TABLE maintenances
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL;
