-- Signup no longer asks free-text company/department/team (this is one company's own intranet, and
-- access is granted per project, not per org unit). A MEMBER applicant instead names the project they
-- want to join; it is only a hint the reviewing admin sees, never automatic membership.
ALTER TABLE app_user ALTER COLUMN job_title SET DATA TYPE VARCHAR(200);
ALTER TABLE app_user ADD COLUMN requested_project_id BIGINT REFERENCES project(id) ON DELETE SET NULL;
