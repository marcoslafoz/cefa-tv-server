ALTER TABLE players ADD COLUMN system_info JSONB;
ALTER TABLE players ADD COLUMN displays JSONB;

ALTER TABLE player_commands ADD COLUMN payload JSONB;

CREATE TABLE player_metrics (
    id BIGSERIAL PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    at TIMESTAMPTZ NOT NULL,
    cpu_percent DOUBLE PRECISION,
    ram_used_mb BIGINT,
    disk_free_mb BIGINT,
    temp_c DOUBLE PRECISION
);

CREATE INDEX idx_player_metrics_player_at ON player_metrics (player_id, at);

CREATE TABLE shell_audits (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL,
    player_name VARCHAR(150) NOT NULL,
    user_id UUID NOT NULL,
    username VARCHAR(150) NOT NULL,
    opened_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    bytes_in BIGINT NOT NULL DEFAULT 0,
    bytes_out BIGINT NOT NULL DEFAULT 0,
    close_reason VARCHAR(40)
);

CREATE INDEX idx_shell_audits_player_opened ON shell_audits (player_id, opened_at DESC);
