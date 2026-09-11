-- lock the final ADMIN/MEMBER model and make long AI work idempotent/retryable.
ALTER TABLE project_member DROP COLUMN IF EXISTS project_role;

ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS request_key VARCHAR(220);
ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS progress INT NOT NULL DEFAULT 0;
ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS result_json TEXT;
ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- processing_job is operational history, so pre-v2.3 duplicate rows are collapsed before the unique key is introduced.
DELETE FROM processing_job older
USING processing_job newer
WHERE older.id < newer.id
  AND older.project_id = newer.project_id
  AND older.job_type = newer.job_type
  AND COALESCE(older.target_type,'') = COALESCE(newer.target_type,'')
  AND COALESCE(older.target_id,-1) = COALESCE(newer.target_id,-1);

UPDATE processing_job
SET request_key = CONCAT(job_type, ':', COALESCE(target_type, 'NONE'), ':', COALESCE(target_id::text, id::text))
WHERE request_key IS NULL OR trim(request_key)='';

ALTER TABLE processing_job ALTER COLUMN request_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_processing_job_request_key ON processing_job(request_key);
CREATE INDEX IF NOT EXISTS idx_processing_job_project_status ON processing_job(project_id,status,updated_at DESC);
