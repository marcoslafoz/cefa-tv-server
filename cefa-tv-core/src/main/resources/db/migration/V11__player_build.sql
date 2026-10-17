ALTER TABLE players ADD COLUMN player_build VARCHAR(40);
ALTER TABLE pairings ADD COLUMN player_build VARCHAR(40);

UPDATE players
SET player_build = system_info ->> 'playerBuild'
WHERE system_info ->> 'playerBuild' IS NOT NULL
  AND length(system_info ->> 'playerBuild') <= 40;
