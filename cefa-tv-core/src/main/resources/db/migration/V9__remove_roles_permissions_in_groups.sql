ALTER TABLE user_groups
    ADD COLUMN permissions TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN builtin BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN all_access BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_groups
SET name = name || ' (previo)'
WHERE LOWER(name) IN ('administradores', 'programadores', 'visualizadores');

INSERT INTO user_groups (id, name, description, permissions, builtin, all_access)
VALUES ('00000000-0000-0000-0000-000000000011', 'Administradores',
        'Acceso total a todos los reproductores, sedes y permisos',
        ARRAY['AUDIT_VIEW','MEDIA_MANAGE','PLAYER_MANAGE','PLAYER_REMOTE','PLAYER_TERMINAL',
              'PLAYER_VIEW','PLAYLIST_MANAGE','SCHEDULE_MANAGE','USER_MANAGE'],
        TRUE, TRUE);

CREATE TEMP TABLE role_groups AS
SELECT r.id AS role_id,
       CASE r.id
           WHEN '00000000-0000-0000-0000-000000000002' THEN '00000000-0000-0000-0000-000000000012'::uuid
           WHEN '00000000-0000-0000-0000-000000000003' THEN '00000000-0000-0000-0000-000000000013'::uuid
           ELSE gen_random_uuid()
       END AS group_id,
       CASE r.id
           WHEN '00000000-0000-0000-0000-000000000002' THEN 'Programadores'
           WHEN '00000000-0000-0000-0000-000000000003' THEN 'Visualizadores'
           ELSE r.name
       END AS group_name,
       r.permissions AS permissions,
       r.name AS role_name
FROM roles r
WHERE r.id <> '00000000-0000-0000-0000-000000000001'
  AND EXISTS (SELECT 1 FROM users u WHERE u.role_id = r.id);

INSERT INTO user_groups (id, name, description, permissions)
SELECT rg.group_id,
       CASE WHEN EXISTS (SELECT 1 FROM user_groups g WHERE LOWER(g.name) = LOWER(rg.group_name))
            THEN rg.group_name || ' (rol)' ELSE rg.group_name END,
       'Migrado desde el rol ' || rg.role_name,
       rg.permissions
FROM role_groups rg;

INSERT INTO user_group_members (user_group_id, user_id)
SELECT '00000000-0000-0000-0000-000000000011'::uuid, u.id
FROM users u
WHERE u.role_id = '00000000-0000-0000-0000-000000000001'
ON CONFLICT DO NOTHING;

INSERT INTO user_group_members (user_group_id, user_id)
SELECT rg.group_id, u.id
FROM users u
JOIN role_groups rg ON rg.role_id = u.role_id
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE personal_groups AS
SELECT u.id AS user_id,
       u.username AS username,
       gen_random_uuid() AS group_id,
       CASE WHEN EXISTS (SELECT 1 FROM user_groups g WHERE LOWER(g.name) = LOWER(u.username))
            THEN u.username || ' (personal)' ELSE u.username END AS group_name
FROM users u
WHERE EXISTS (SELECT 1 FROM access_grants a WHERE a.user_id = u.id);

INSERT INTO user_groups (id, name, description, permissions)
SELECT pg.group_id, pg.group_name,
       'Grupo personal migrado de las concesiones directas de ' || pg.username, '{}'
FROM personal_groups pg;

INSERT INTO user_group_members (user_group_id, user_id)
SELECT pg.group_id, pg.user_id FROM personal_groups pg;

UPDATE access_grants a
SET user_group_id = pg.group_id, user_id = NULL
FROM personal_groups pg
WHERE a.user_id = pg.user_id;

ALTER TABLE access_grants DROP CONSTRAINT access_grants_one_subject;
DROP INDEX IF EXISTS uq_access_grants_user;
DROP INDEX IF EXISTS uq_access_grants_user_site;
ALTER TABLE access_grants DROP COLUMN user_id;
ALTER TABLE access_grants ALTER COLUMN user_group_id SET NOT NULL;

ALTER TABLE users DROP COLUMN role_id;
DROP TABLE roles;
