CREATE TABLE project_memory (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  memory_key VARCHAR(200) NOT NULL,
  memory_value TEXT NOT NULL,
  memory_type VARCHAR(40) NOT NULL DEFAULT 'FACT',
  review_status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED',
  source_type VARCHAR(40),
  source_id BIGINT,
  created_by BIGINT NOT NULL REFERENCES app_user(id),
  updated_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(project_id, memory_key)
);

CREATE TABLE timeline_event (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  event_type VARCHAR(40) NOT NULL,
  title VARCHAR(500) NOT NULL,
  description TEXT,
  happened_at TIMESTAMPTZ NOT NULL,
  source_type VARCHAR(40),
  source_id BIGINT,
  evidence_id BIGINT REFERENCES evidence(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE connector_sync_state (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  connector_type VARCHAR(30) NOT NULL,
  external_scope VARCHAR(1000) NOT NULL,
  cursor_value TEXT,
  last_synced_at TIMESTAMPTZ,
  last_status VARCHAR(30),
  last_error TEXT,
  UNIQUE(project_id, connector_type, external_scope)
);

CREATE INDEX idx_memory_project ON project_memory(project_id, updated_at DESC);
CREATE INDEX idx_timeline_project_time ON timeline_event(project_id, happened_at DESC);
