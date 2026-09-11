-- H2 mirrors signup approval columns for isolated tests only.
ALTER TABLE app_user ADD COLUMN company_name VARCHAR(200);
ALTER TABLE app_user ADD COLUMN department_name VARCHAR(200);
ALTER TABLE app_user ADD COLUMN signup_note VARCHAR(1000);
ALTER TABLE app_user ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE app_user ADD COLUMN approved_by BIGINT;
ALTER TABLE app_user ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN rejected_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE app_user ADD COLUMN rejection_reason VARCHAR(1000);
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_approval_status CHECK (approval_status IN ('PENDING','APPROVED','REJECTED'));
UPDATE app_user SET approval_status='APPROVED', approved_at=COALESCE(approved_at,created_at) WHERE approval_status='APPROVED';
CREATE INDEX idx_app_user_approval_status ON app_user(approval_status,created_at);
