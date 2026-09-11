CREATE TABLE project_search_rule (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  name VARCHAR(200) NOT NULL,
  aliases_json TEXT NOT NULL,
  patterns_json TEXT NOT NULL,
  target_file VARCHAR(500),
  mode VARCHAR(20) NOT NULL DEFAULT 'SMART' CHECK (mode IN ('SMART','FULL')),
  priority INT NOT NULL DEFAULT 100,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_project_search_rule_project ON project_search_rule(project_id, active, priority DESC);

ALTER TABLE document_version ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE document_version ADD COLUMN embedding_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE document_version ADD COLUMN embedding_last_error TEXT;
ALTER TABLE document_version ADD COLUMN embedding_updated_at TIMESTAMPTZ;
UPDATE document_version v
SET embedding_status='READY', embedding_updated_at=now()
WHERE EXISTS (
  SELECT 1 FROM document_chunk c
  WHERE c.version_id=v.id AND (c.embedding_vector IS NOT NULL OR c.embedding_json IS NOT NULL)
);
CREATE INDEX idx_document_version_embedding_retry ON document_version(embedding_status, embedding_attempts, id);
