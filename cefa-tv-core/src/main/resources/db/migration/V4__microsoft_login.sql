ALTER TABLE users
    ADD COLUMN email VARCHAR(320),
    ADD COLUMN auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN microsoft_object_id VARCHAR(64);

ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT users_auth_provider_check CHECK (auth_provider IN ('LOCAL', 'MICROSOFT', 'HYBRID'));

CREATE UNIQUE INDEX users_email_lower_idx ON users (LOWER(email)) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX users_microsoft_object_id_idx ON users (microsoft_object_id) WHERE microsoft_object_id IS NOT NULL;
