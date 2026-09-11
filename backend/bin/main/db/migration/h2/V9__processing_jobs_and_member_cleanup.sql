-- H2 mirrors the final ADMIN/MEMBER model for unit tests only; PostgreSQL remains the integration DB.
ALTER TABLE project_member DROP COLUMN IF EXISTS project_role;

ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS request_key VARCHAR(220);
ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS progress INT NOT NULL DEFAULT 0;
ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS result_json CLOB;
ALTER TABLE processing_job ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

DELETE FROM processing_job
WHERE id NOT IN (
  SELECT MAX(id) FROM processing_job
  GROUP BY project_id,job_type,target_type,target_id
);

UPDATE processing_job
SET request_key = CONCAT(job_type, ':', COALESCE(target_type, 'NONE'), ':', COALESCE(CAST(target_id AS VARCHAR), CAST(id AS VARCHAR)))
WHERE request_key IS NULL OR TRIM(request_key)='';

ALTER TABLE processing_job ALTER COLUMN request_key SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_processing_job_request_key ON processing_job(request_key);
CREATE INDEX IF NOT EXISTS idx_processing_job_project_status ON processing_job(project_id,status,updated_at);
