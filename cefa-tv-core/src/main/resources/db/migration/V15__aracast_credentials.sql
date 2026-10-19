ALTER TABLE aracast_players ADD COLUMN ssh_password_enc TEXT;
ALTER TABLE aracast_players ADD COLUMN ftp_password_enc TEXT;
ALTER TABLE aracast_players ADD COLUMN published_hash VARCHAR(64);
ALTER TABLE aracast_players ADD COLUMN published_at TIMESTAMPTZ;

ALTER TABLE stored_files ADD COLUMN sha1 VARCHAR(40);
