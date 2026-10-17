CREATE TABLE sites (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    address VARCHAR(300),
    city VARCHAR(100),
    country VARCHAR(100),
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Madrid',
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_sites_name ON sites (LOWER(name));

ALTER TABLE players
    ADD COLUMN description VARCHAR(500),
    ADD COLUMN site_id UUID REFERENCES sites (id);

CREATE INDEX idx_players_site ON players (site_id);

ALTER TABLE access_grants ALTER COLUMN player_group_id DROP NOT NULL;
ALTER TABLE access_grants ADD COLUMN site_id UUID REFERENCES sites (id) ON DELETE CASCADE;
ALTER TABLE access_grants
    ADD CONSTRAINT access_grants_one_target CHECK ((player_group_id IS NULL) <> (site_id IS NULL));

CREATE UNIQUE INDEX uq_access_grants_user_group_site
    ON access_grants (user_group_id, site_id) WHERE user_group_id IS NOT NULL AND site_id IS NOT NULL;
CREATE UNIQUE INDEX uq_access_grants_user_site
    ON access_grants (user_id, site_id) WHERE user_id IS NOT NULL AND site_id IS NOT NULL;

ALTER TABLE schedule_targets DROP CONSTRAINT schedule_targets_check;
ALTER TABLE schedule_targets ADD COLUMN site_id UUID REFERENCES sites (id);
ALTER TABLE schedule_targets
    ADD CONSTRAINT schedule_targets_one_target CHECK (
        (CASE WHEN player_id IS NULL THEN 0 ELSE 1 END)
      + (CASE WHEN group_id IS NULL THEN 0 ELSE 1 END)
      + (CASE WHEN site_id IS NULL THEN 0 ELSE 1 END) = 1);
CREATE INDEX idx_schedule_targets_site ON schedule_targets (site_id);
