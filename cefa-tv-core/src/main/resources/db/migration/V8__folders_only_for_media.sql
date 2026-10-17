ALTER TABLE playlists DROP COLUMN folder_id;

DELETE FROM folders WHERE kind = 'PLAYLIST';

DROP INDEX uq_folders_sibling_name;

ALTER TABLE folders DROP COLUMN kind;

CREATE UNIQUE INDEX uq_folders_sibling_name
    ON folders (COALESCE(parent_id, '00000000-0000-0000-0000-000000000000'::uuid), LOWER(name));
