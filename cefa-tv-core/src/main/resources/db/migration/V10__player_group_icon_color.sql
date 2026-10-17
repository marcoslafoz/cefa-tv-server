ALTER TABLE player_groups
    ADD COLUMN icon VARCHAR(64),
    ADD COLUMN color VARCHAR(20);

ALTER TABLE player_groups
    ADD CONSTRAINT player_groups_color_check CHECK (
        color IS NULL OR color IN ('slate', 'blue', 'green', 'amber', 'red', 'violet', 'pink', 'teal'));
