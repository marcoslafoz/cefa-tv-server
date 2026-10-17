CREATE TABLE folders (
    id UUID PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    parent_id UUID REFERENCES folders (id),
    created_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(100),
    CONSTRAINT folders_kind_check CHECK (kind IN ('MEDIA', 'PLAYLIST')),
    CONSTRAINT folders_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE UNIQUE INDEX uq_folders_sibling_name
    ON folders (kind, COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name));
CREATE INDEX idx_folders_parent ON folders (parent_id);

ALTER TABLE media_assets ADD COLUMN folder_id UUID REFERENCES folders (id);
ALTER TABLE playlists ADD COLUMN folder_id UUID REFERENCES folders (id);

CREATE INDEX idx_media_assets_folder ON media_assets (folder_id);
CREATE INDEX idx_playlists_folder ON playlists (folder_id);
