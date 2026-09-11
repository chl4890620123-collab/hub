ALTER TABLE app_user ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE app_user ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE refresh_token (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ,
  revoked_reason VARCHAR(100),
  user_agent VARCHAR(500),
  ip_address VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_expiry ON refresh_token(expires_at);
