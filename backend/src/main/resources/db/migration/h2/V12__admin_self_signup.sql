-- H2 mirror for isolated tests only.
ALTER TABLE app_user ADD COLUMN team_name VARCHAR(200);
ALTER TABLE app_user ADD COLUMN requested_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER';
UPDATE app_user SET requested_role=global_role WHERE approval_status='APPROVED';
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_requested_role CHECK (requested_role IN ('ADMIN','MEMBER'));
CREATE INDEX idx_app_user_signup_role_status ON app_user(requested_role,approval_status,created_at);
CREATE TABLE system_guard (
  guard_key VARCHAR(80) PRIMARY KEY,
  guard_value VARCHAR(200) NOT NULL
);
MERGE INTO system_guard(guard_key,guard_value) KEY(guard_key) VALUES('FIRST_ADMIN','OPEN');
