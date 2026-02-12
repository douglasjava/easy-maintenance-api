CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    org_code VARCHAR(100),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    changed_by_user_id VARCHAR(100),
    changed_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    diff_json TEXT,
    request_id VARCHAR(100),
    ip VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX idx_audit_logs_org_code ON audit_logs(org_code);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_logs_changed_at ON audit_logs(changed_at);