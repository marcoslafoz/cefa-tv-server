CREATE TABLE roles (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    builtin BOOLEAN NOT NULL DEFAULT FALSE,
    permissions TEXT[] NOT NULL DEFAULT '{}'
);

INSERT INTO roles (id, name, builtin, permissions) VALUES
    ('00000000-0000-0000-0000-000000000001', 'ADMIN', TRUE,
     ARRAY['MEDIA_MANAGE','PLAYLIST_MANAGE','SCHEDULE_MANAGE','PLAYER_VIEW','PLAYER_MANAGE','PLAYER_REMOTE','PLAYER_TERMINAL','USER_MANAGE']),
    ('00000000-0000-0000-0000-000000000002', 'SCHEDULER', TRUE,
     ARRAY['MEDIA_MANAGE','PLAYLIST_MANAGE','SCHEDULE_MANAGE','PLAYER_VIEW','PLAYER_REMOTE']),
    ('00000000-0000-0000-0000-000000000003', 'VIEWER', TRUE,
     ARRAY['PLAYER_VIEW']);

ALTER TABLE users
    ADD COLUMN display_name VARCHAR(150),
    ADD COLUMN role_id UUID REFERENCES roles (id),
    ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE users
SET display_name = username,
    role_id = CASE role
        WHEN 'ADMIN' THEN '00000000-0000-0000-0000-000000000001'::uuid
        ELSE '00000000-0000-0000-0000-000000000002'::uuid
    END;

ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE users DROP COLUMN role;

CREATE TABLE user_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500)
);

CREATE TABLE user_group_members (
    user_group_id UUID NOT NULL REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    PRIMARY KEY (user_group_id, user_id)
);

CREATE INDEX idx_user_group_members_user ON user_group_members (user_id);

CREATE TABLE access_grants (
    id UUID PRIMARY KEY,
    user_group_id UUID REFERENCES user_groups (id) ON DELETE CASCADE,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    player_group_id UUID NOT NULL REFERENCES player_groups (id) ON DELETE CASCADE,
    level VARCHAR(20) NOT NULL,
    CONSTRAINT access_grants_one_subject CHECK ((user_group_id IS NULL) <> (user_id IS NULL))
);

CREATE UNIQUE INDEX uq_access_grants_user_group
    ON access_grants (user_group_id, player_group_id) WHERE user_group_id IS NOT NULL;
CREATE UNIQUE INDEX uq_access_grants_user
    ON access_grants (user_id, player_group_id) WHERE user_id IS NOT NULL;

DROP TABLE enrollment_keys;

CREATE TABLE pairings (
    id UUID PRIMARY KEY,
    code VARCHAR(10) NOT NULL,
    secret_hash VARCHAR(64) NOT NULL,
    hostname VARCHAR(255),
    ip VARCHAR(100),
    hardware JSONB,
    resolution VARCHAR(50),
    player_version VARCHAR(50),
    timezone VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    player_id UUID REFERENCES players (id) ON DELETE SET NULL,
    approved_by VARCHAR(100),
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_pairings_pending_code ON pairings (code) WHERE status = 'PENDING';
CREATE INDEX idx_pairings_status ON pairings (status, expires_at);
CREATE INDEX idx_pairings_player ON pairings (player_id);

CREATE TABLE pairing_audits (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    player_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pairing_audits_created ON pairing_audits (created_at DESC);
