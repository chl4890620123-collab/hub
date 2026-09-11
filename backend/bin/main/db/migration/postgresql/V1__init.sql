CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE app_user (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  global_role VARCHAR(20) NOT NULL CHECK (global_role IN ('ADMIN','MEMBER')),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE project (
  id BIGSERIAL PRIMARY KEY, name VARCHAR(200) NOT NULL, description TEXT,
  created_by BIGINT NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE project_member (
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  project_role VARCHAR(20) NOT NULL CHECK (project_role IN ('LEAD','MEMBER')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (project_id,user_id)
);
CREATE TABLE document (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  source_type VARCHAR(40) NOT NULL, source_identifier VARCHAR(1000) NOT NULL,
  original_name VARCHAR(500) NOT NULL, storage_path TEXT, archived BOOLEAN NOT NULL DEFAULT FALSE,
  source_deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(project_id,source_type,source_identifier)
);
CREATE TABLE document_version (
  id BIGSERIAL PRIMARY KEY, document_id BIGINT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  version_no INT NOT NULL, sha256 VARCHAR(64) NOT NULL, full_text TEXT NOT NULL,
  parse_status VARCHAR(30) NOT NULL, summary TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(document_id,version_no)
);
CREATE TABLE document_chunk (
  id BIGSERIAL PRIMARY KEY, version_id BIGINT NOT NULL REFERENCES document_version(id) ON DELETE CASCADE,
  chunk_index INT NOT NULL, heading VARCHAR(500), page_no INT, paragraph_ref VARCHAR(100),
  content TEXT NOT NULL, embedding_json TEXT, embedding_vector vector(768), UNIQUE(version_id,chunk_index)
);
CREATE TABLE meeting (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  title VARCHAR(500) NOT NULL, audio_path TEXT, meeting_at TIMESTAMPTZ, stt_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  transcript_text TEXT, created_by BIGINT NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE transcript_segment (
  id BIGSERIAL PRIMARY KEY, meeting_id BIGINT NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
  segment_index INT NOT NULL, start_ms BIGINT, end_ms BIGINT, speaker VARCHAR(200), text TEXT NOT NULL,
  UNIQUE(meeting_id,segment_index)
);
CREATE TABLE evidence (
  id BIGSERIAL PRIMARY KEY, version_id BIGINT REFERENCES document_version(id), chunk_id BIGINT REFERENCES document_chunk(id),
  transcript_segment_id BIGINT REFERENCES transcript_segment(id), evidence_text TEXT NOT NULL,
  start_offset INT, end_offset INT, content_hash VARCHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK(version_id IS NOT NULL OR transcript_segment_id IS NOT NULL)
);
CREATE TABLE ai_run (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  document_version_id BIGINT REFERENCES document_version(id), meeting_id BIGINT REFERENCES meeting(id),
  kind VARCHAR(40) NOT NULL, status VARCHAR(30) NOT NULL, provider VARCHAR(40), raw_json TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE todo (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  source_document_version_id BIGINT REFERENCES document_version(id), source_meeting_id BIGINT REFERENCES meeting(id),
  title VARCHAR(500) NOT NULL, description TEXT, assignee_id BIGINT REFERENCES app_user(id), assignee_text VARCHAR(200),
  assignee_suggestion_text VARCHAR(200), due_date DATE, due_date_suggestion DATE, confidence VARCHAR(20),
  review_status VARCHAR(30) NOT NULL DEFAULT 'AI_GENERATED' CHECK(review_status IN ('AI_GENERATED','REVIEWING','CONFIRMED','REJECTED')),
  task_status VARCHAR(30) NOT NULL DEFAULT 'TODO' CHECK(task_status IN ('TODO','IN_PROGRESS','DONE','BLOCKED')),
  confirmed_by BIGINT REFERENCES app_user(id), confirmed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE todo_evidence (todo_id BIGINT NOT NULL REFERENCES todo(id) ON DELETE CASCADE,evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,PRIMARY KEY(todo_id,evidence_id));
CREATE TABLE decision_candidate (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  source_document_version_id BIGINT REFERENCES document_version(id), source_meeting_id BIGINT REFERENCES meeting(id),
  statement TEXT NOT NULL, confidence VARCHAR(20), review_status VARCHAR(30) NOT NULL DEFAULT 'AI_GENERATED'
  CHECK(review_status IN ('AI_GENERATED','REVIEWING','CONFIRMED','REJECTED')),
  confirmed_by BIGINT REFERENCES app_user(id), confirmed_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE decision_evidence (decision_id BIGINT NOT NULL REFERENCES decision_candidate(id) ON DELETE CASCADE,evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,PRIMARY KEY(decision_id,evidence_id));
CREATE TABLE change_analysis (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  before_version_id BIGINT NOT NULL REFERENCES document_version(id), after_version_id BIGINT NOT NULL REFERENCES document_version(id),
  status VARCHAR(30) NOT NULL DEFAULT 'AI_GENERATED', created_by BIGINT NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE change_item (
  id BIGSERIAL PRIMARY KEY, analysis_id BIGINT NOT NULL REFERENCES change_analysis(id) ON DELETE CASCADE,
  category VARCHAR(40), before_text TEXT, after_text TEXT, reason TEXT, review_status VARCHAR(30) NOT NULL DEFAULT 'AI_GENERATED'
);
CREATE TABLE change_evidence (
  change_item_id BIGINT NOT NULL REFERENCES change_item(id) ON DELETE CASCADE,
  evidence_id BIGINT NOT NULL REFERENCES evidence(id) ON DELETE CASCADE,
  evidence_side VARCHAR(10) NOT NULL CHECK(evidence_side IN ('BEFORE','AFTER')), PRIMARY KEY(change_item_id,evidence_id,evidence_side)
);
CREATE TABLE revision_history (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE, entity_type VARCHAR(40) NOT NULL,
  entity_id BIGINT NOT NULL, actor_id BIGINT NOT NULL REFERENCES app_user(id), action VARCHAR(30) NOT NULL,
  before_json TEXT, after_json TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE feedback (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  entity_type VARCHAR(40) NOT NULL, entity_id BIGINT NOT NULL, field_name VARCHAR(100), ai_value TEXT, human_value TEXT,
  feedback_type VARCHAR(50), created_by BIGINT NOT NULL REFERENCES app_user(id), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE search_log (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES app_user(id), query_text VARCHAR(1000) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE connector_account (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  project_id BIGINT REFERENCES project(id) ON DELETE CASCADE, connector_type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'CONNECTED', external_account_id VARCHAR(500), access_token_enc TEXT, refresh_token_enc TEXT,
  expires_at TIMESTAMPTZ, scope TEXT, config_json TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE external_item (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  connector_account_id BIGINT REFERENCES connector_account(id) ON DELETE SET NULL, external_id VARCHAR(1000) NOT NULL,
  item_type VARCHAR(60) NOT NULL, title VARCHAR(1000), content TEXT, author VARCHAR(500), source_url TEXT,
  source_created_at TIMESTAMPTZ, raw_metadata TEXT, imported_document_id BIGINT REFERENCES document(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(project_id,connector_account_id,external_id)
);
CREATE TABLE processing_job (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  job_type VARCHAR(50) NOT NULL, target_type VARCHAR(50), target_id BIGINT, status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  error_code VARCHAR(100), error_message TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), started_at TIMESTAMPTZ, finished_at TIMESTAMPTZ
);
CREATE TABLE audit_log (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT REFERENCES app_user(id), project_id BIGINT REFERENCES project(id),
  action VARCHAR(100) NOT NULL, target_type VARCHAR(50), target_id BIGINT, detail_json TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_project_member_user ON project_member(user_id);
CREATE INDEX idx_document_project ON document(project_id,archived);
CREATE INDEX idx_version_document ON document_version(document_id,version_no DESC);
CREATE INDEX idx_chunk_version ON document_chunk(version_id,chunk_index);
CREATE INDEX idx_todo_project_due ON todo(project_id,due_date);
CREATE INDEX idx_todo_assignee_status ON todo(assignee_id,task_status);
CREATE INDEX idx_todo_review ON todo(project_id,review_status);
CREATE INDEX idx_search_project_query ON search_log(project_id,query_text);
CREATE INDEX idx_external_project_type ON external_item(project_id,item_type);
CREATE INDEX idx_chunk_embedding_hnsw ON document_chunk USING hnsw (embedding_vector vector_cosine_ops) WHERE embedding_vector IS NOT NULL;
