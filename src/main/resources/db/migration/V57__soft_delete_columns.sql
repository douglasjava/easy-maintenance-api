-- TASK-018: Soft delete nas entidades críticas
-- Adds deleted_at column to the four core entities.
-- NULL  = active record (not deleted)
-- value = soft-deleted timestamp
--
-- @SQLDelete on each entity intercepts Hibernate's DELETE and issues:
--   UPDATE <table> SET deleted_at = now() WHERE id = ?
-- @SQLRestriction("deleted_at IS NULL") on each entity ensures all JPA
-- queries automatically exclude soft-deleted rows.

ALTER TABLE maintenance_items
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

ALTER TABLE maintenances
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

ALTER TABLE users
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

ALTER TABLE organizations
    ADD COLUMN deleted_at TIMESTAMP NULL DEFAULT NULL;

-- Covering indexes: the @SQLRestriction filter is added to every query,
-- so this index keeps WHERE deleted_at IS NULL lookups fast.
CREATE INDEX idx_maintenance_items_deleted_at ON maintenance_items (deleted_at);
CREATE INDEX idx_maintenances_deleted_at       ON maintenances (deleted_at);
CREATE INDEX idx_users_deleted_at              ON users (deleted_at);
CREATE INDEX idx_organizations_deleted_at      ON organizations (deleted_at);
