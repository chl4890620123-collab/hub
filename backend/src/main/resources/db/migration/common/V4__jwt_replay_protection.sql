ALTER TABLE app_user ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE refresh_token ADD COLUMN family_id VARCHAR(64);
UPDATE refresh_token SET family_id=token_hash WHERE family_id IS NULL;
CREATE INDEX idx_refresh_token_family ON refresh_token(family_id);
