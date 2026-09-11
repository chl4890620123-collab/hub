ALTER TABLE todo ADD COLUMN assignee_suggestion_id BIGINT REFERENCES app_user(id);
CREATE INDEX idx_todo_assignee_suggestion ON todo(assignee_suggestion_id);
