CREATE TABLE admin_submission (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  sender_id BIGINT NOT NULL REFERENCES app_user(id),
  title VARCHAR(500) NOT NULL,
  note VARCHAR(1000),
  external_url VARCHAR(2000),
  file_name VARCHAR(500),
  content_type VARCHAR(200),
  size_bytes BIGINT,
  storage_path TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_admin_submission_project ON admin_submission(project_id, id DESC);
CREATE INDEX idx_admin_submission_sender ON admin_submission(sender_id, id DESC);
