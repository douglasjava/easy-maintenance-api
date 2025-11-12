-- V1: Initial schema (MVP) - MySQL 8
-- Tabelas: organizations, users, norms, maintenance_items, maintenances

-- ============== ORGANIZATIONS & USERS ==============
CREATE TABLE IF NOT EXISTS organizations (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    code         CHAR(36)      NOT NULL,
    name         VARCHAR(160)  NOT NULL,
    plan         VARCHAR(40)   NOT NULL,
    city         VARCHAR(120)  NULL,
    doc          VARCHAR(40)   NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_organizations_code (code),
    KEY idx_organizations_name (name),
    KEY idx_organizations_plan (plan)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    organization_code CHAR(36)      NOT NULL,
    email             VARCHAR(160)  NOT NULL,
    name              VARCHAR(160)  NOT NULL,
    role              VARCHAR(40)   NOT NULL,      -- ADMIN/SYNDIC/TECH/READER
    status            VARCHAR(20)   NOT NULL,      -- ACTIVE/INACTIVE
    password_hash     VARCHAR(200)  NOT NULL,
    created_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email),
    KEY idx_users_org (organization_code),
    CONSTRAINT fk_users_org FOREIGN KEY (organization_code)
    REFERENCES organizations(code) ON UPDATE CASCADE ON DELETE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =================== CATALOG: NORMS =================
CREATE TABLE IF NOT EXISTS norms (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    item_type       VARCHAR(50)   NOT NULL,      -- EXTINGUISHER/SPDA/WATER_TANK/AIR_COND/LIGHTING/HYDRANT/OTHER
    period_unit     VARCHAR(20)   NOT NULL,      -- DAYS/MONTHS
    period_qty      INT           NOT NULL,      -- > 0
    tolerance_days  INT           NOT NULL DEFAULT 0,
    authority       VARCHAR(120)  NOT NULL,      -- ABNT/ANVISA/CBM/MUNICIPAL
    doc_url         VARCHAR(500)  NULL,
    notes           VARCHAR(500)  NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_norms_item_type (item_type),
    KEY idx_norms_authority (authority)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ========= ASSETS & MAINTENANCES (CORE) ============
CREATE TABLE IF NOT EXISTS maintenance_items (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    organization_code  CHAR(36)      NOT NULL,
    item_type          VARCHAR(50)   NOT NULL,
    norm_id            BIGINT        NULL,
    location_json      JSON          NULL,       -- {block, area, details}
    last_performed_at  DATE          NULL,
    next_due_at        DATE          NOT NULL,
    status             VARCHAR(20)   NOT NULL,   -- OK/NEAR_DUE/OVERDUE
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_items_org_status (organization_code, status),
    KEY idx_items_next_due (next_due_at),
    KEY idx_items_item_type (item_type),
    KEY idx_items_norm (norm_id),
    CONSTRAINT fk_items_org FOREIGN KEY (organization_code)
    REFERENCES organizations(code) ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_items_norm FOREIGN KEY (norm_id)
    REFERENCES norms(id) ON UPDATE CASCADE ON DELETE SET NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS maintenances (
    id                       BIGINT NOT NULL AUTO_INCREMENT,
    item_id                  BIGINT        NOT NULL,
    performed_at             DATE          NOT NULL,
    issued_by                VARCHAR(160)  NULL,
    certificate_number       VARCHAR(120)  NULL,
    certificate_valid_until  DATE          NULL,
    receipt_url              VARCHAR(600)  NULL,
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_maint_item_date (item_id, performed_at),
    KEY idx_maint_cert (certificate_number),
    CONSTRAINT fk_maint_item FOREIGN KEY (item_id)
    REFERENCES maintenance_items(id) ON UPDATE CASCADE ON DELETE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;