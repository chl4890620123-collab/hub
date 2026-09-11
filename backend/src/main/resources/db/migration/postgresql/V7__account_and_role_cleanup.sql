ALTER TABLE app_user ADD COLUMN login_id VARCHAR(80);
ALTER TABLE app_user ADD COLUMN job_title VARCHAR(120);
ALTER TABLE app_user ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE app_user SET login_id=CONCAT('user-', id) WHERE login_id IS NULL OR trim(login_id)='';
ALTER TABLE app_user ALTER COLUMN login_id SET NOT NULL;
CREATE UNIQUE INDEX uq_app_user_login_id ON app_user(login_id);
UPDATE project_member SET project_role='MEMBER' WHERE project_role='LEAD';
DELETE FROM project_member pm USING app_user u WHERE pm.user_id=u.id AND u.global_role='ADMIN';
