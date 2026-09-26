ALTER TABLE processing_job ADD COLUMN requester_user_id BIGINT;
ALTER TABLE processing_job ADD CONSTRAINT fk_processing_job_requester FOREIGN KEY (requester_user_id) REFERENCES app_user(id);
CREATE INDEX idx_processing_job_requester_updated ON processing_job(requester_user_id, updated_at);
