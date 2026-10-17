CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE media_assets (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    mime VARCHAR(150),
    size_bytes BIGINT NOT NULL DEFAULT 0,
    sha256 VARCHAR(64),
    object_key VARCHAR(255),
    thumbnail_key VARCHAR(255),
    duration_ms BIGINT,
    width INT,
    height INT,
    page_count INT,
    url TEXT,
    refresh_sec INT,
    pages JSONB,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100)
);

CREATE INDEX idx_media_assets_type ON media_assets (type);
CREATE INDEX idx_media_assets_created_at ON media_assets (created_at DESC);

CREATE TABLE stored_files (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES media_assets (id) ON DELETE CASCADE,
    sha256 VARCHAR(64) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    mime VARCHAR(150) NOT NULL,
    name VARCHAR(255) NOT NULL
);

CREATE INDEX idx_stored_files_sha256 ON stored_files (sha256);
CREATE INDEX idx_stored_files_asset ON stored_files (asset_id);

CREATE TABLE playlists (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE playlist_items (
    id UUID PRIMARY KEY,
    playlist_id UUID NOT NULL REFERENCES playlists (id) ON DELETE CASCADE,
    position INT NOT NULL,
    asset_id UUID NOT NULL REFERENCES media_assets (id),
    duration_sec INT NOT NULL,
    fit VARCHAR(20) NOT NULL DEFAULT 'COVER',
    muted BOOLEAN NOT NULL DEFAULT TRUE,
    settings JSONB
);

CREATE INDEX idx_playlist_items_playlist ON playlist_items (playlist_id, position);
CREATE INDEX idx_playlist_items_asset ON playlist_items (asset_id);

CREATE TABLE player_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    fallback_playlist_id UUID REFERENCES playlists (id) ON DELETE SET NULL
);

CREATE TABLE players (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    group_id UUID REFERENCES player_groups (id) ON DELETE SET NULL,
    fallback_playlist_id UUID REFERENCES playlists (id) ON DELETE SET NULL,
    status VARCHAR(20) NOT NULL,
    device_token_hash VARCHAR(64) NOT NULL UNIQUE,
    hostname VARCHAR(255),
    ip VARCHAR(64),
    hardware JSONB,
    resolution VARCHAR(30),
    orientation VARCHAR(20),
    player_version VARCHAR(50),
    timezone VARCHAR(60),
    last_seen_at TIMESTAMPTZ,
    desired_version BIGINT NOT NULL DEFAULT 0,
    applied_version BIGINT NOT NULL DEFAULT 0,
    state VARCHAR(30),
    current_playlist_id UUID,
    current_asset_id UUID,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE enrollment_keys (
    id UUID PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    group_id UUID REFERENCES player_groups (id) ON DELETE SET NULL,
    max_uses INT,
    uses INT NOT NULL DEFAULT 0,
    expires_at TIMESTAMPTZ,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE schedules (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    playlist_id UUID NOT NULL REFERENCES playlists (id),
    priority INT NOT NULL DEFAULT 10,
    start_date DATE,
    end_date DATE,
    start_time TIME,
    end_time TIME,
    days_of_week INT[] NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_schedules_playlist ON schedules (playlist_id);

CREATE TABLE schedule_targets (
    id UUID PRIMARY KEY,
    schedule_id UUID NOT NULL REFERENCES schedules (id) ON DELETE CASCADE,
    player_id UUID REFERENCES players (id) ON DELETE CASCADE,
    group_id UUID REFERENCES player_groups (id) ON DELETE CASCADE,
    CHECK ((player_id IS NOT NULL) <> (group_id IS NOT NULL))
);

CREATE INDEX idx_schedule_targets_schedule ON schedule_targets (schedule_id);

CREATE TABLE player_manifests (
    player_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    version BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    manifest JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (player_id, version)
);

CREATE TABLE player_deliveries (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    version BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX idx_player_deliveries_player ON player_deliveries (player_id, version DESC);

CREATE TABLE player_commands (
    id UUID PRIMARY KEY,
    player_id UUID NOT NULL REFERENCES players (id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    delivered_at TIMESTAMPTZ
);

CREATE INDEX idx_player_commands_pending ON player_commands (player_id) WHERE delivered_at IS NULL;
