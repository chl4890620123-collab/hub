-- Organize project-shared repositories/documents by department/team without granting access from free-text profile fields.
ALTER TABLE project ADD COLUMN department_name VARCHAR(200);
ALTER TABLE project ADD COLUMN team_name VARCHAR(200);
CREATE INDEX idx_project_org_unit ON project(department_name, team_name);

-- Soft-delete todos so mistaken extraction/entry can be restored from a project trash view.
ALTER TABLE todo ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE todo ADD COLUMN deleted_by BIGINT REFERENCES app_user(id);
CREATE INDEX idx_todo_project_deleted ON todo(project_id, deleted_at);
