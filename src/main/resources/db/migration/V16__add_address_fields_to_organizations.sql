ALTER TABLE organizations
    ADD COLUMN street        VARCHAR(160) DEFAULT NULL,
    ADD COLUMN number        VARCHAR(20)  DEFAULT NULL,
    ADD COLUMN complement    VARCHAR(80)  DEFAULT NULL,
    ADD COLUMN neighborhood  VARCHAR(120) DEFAULT NULL,
    ADD COLUMN state         CHAR(2)       DEFAULT NULL,
    ADD COLUMN zip_code      VARCHAR(10)   DEFAULT NULL,
    ADD COLUMN country       VARCHAR(60)   DEFAULT 'BR';
