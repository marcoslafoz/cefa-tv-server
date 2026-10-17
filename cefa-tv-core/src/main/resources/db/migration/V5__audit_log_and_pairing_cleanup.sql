CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    at TIMESTAMPTZ NOT NULL,
    user_id UUID,
    username VARCHAR(150),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    entity_name VARCHAR(255),
    outcome VARCHAR(20) NOT NULL,
    details JSONB,
    ip VARCHAR(100),
    user_agent VARCHAR(400),
    CONSTRAINT audit_logs_outcome_check CHECK (outcome IN ('SUCCESS', 'DENIED', 'FAILED'))
);

CREATE INDEX idx_audit_logs_at ON audit_logs (at DESC);
CREATE INDEX idx_audit_logs_user_at ON audit_logs (user_id, at DESC);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, at DESC);
CREATE INDEX idx_audit_logs_action ON audit_logs (action, at DESC);

CREATE TABLE audit_settings (
    id UUID PRIMARY KEY,
    retention_days INT NOT NULL,
    CONSTRAINT audit_settings_retention_check CHECK (retention_days >= 30)
);

INSERT INTO audit_settings (id, retention_days)
VALUES ('00000000-0000-0000-0000-000000000005', 365);

UPDATE roles
SET permissions = array_append(permissions, 'AUDIT_VIEW')
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND NOT ('AUDIT_VIEW' = ANY (permissions));

ALTER TABLE players ADD COLUMN first_connected_at TIMESTAMPTZ;

UPDATE players
SET first_connected_at = last_seen_at
WHERE last_seen_at IS NOT NULL
  AND last_seen_at > created_at + INTERVAL '5 seconds';

ALTER TABLE players ADD COLUMN preferred_output VARCHAR(100);
