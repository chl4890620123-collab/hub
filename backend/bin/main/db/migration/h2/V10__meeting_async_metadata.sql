-- H2 test schema mirrors the PostgreSQL meeting job metadata.
ALTER TABLE meeting ADD COLUMN IF NOT EXISTS audio_file_name VARCHAR(500);
ALTER TABLE meeting ADD COLUMN IF NOT EXISTS audio_content_type VARCHAR(200);
ALTER TABLE meeting ADD COLUMN IF NOT EXISTS source_date DATE;
ALTER TABLE meeting ADD COLUMN IF NOT EXISTS audio_sha256 VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_meeting_project_audio_hash ON meeting(project_id,audio_sha256);
