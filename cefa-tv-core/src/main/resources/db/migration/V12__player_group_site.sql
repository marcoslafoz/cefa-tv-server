ALTER TABLE player_groups ADD COLUMN site_id UUID REFERENCES sites (id);

CREATE INDEX idx_player_groups_site ON player_groups (site_id);
