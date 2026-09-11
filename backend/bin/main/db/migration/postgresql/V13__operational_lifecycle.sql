-- operational lifecycle hardening.
-- Accounts are never physically deleted; project membership changes keep history; unfinished work is queued for reassignment.
ALTER TABLE app_user ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
UPDATE app_user
SET account_status = CASE
  WHEN approval_status='APPROVED' AND active=TRUE THEN 'ACTIVE'
  ELSE 'SUSPENDED'
END;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_account_status CHECK (account_status IN ('ACTIVE','SUSPENDED','WITHDRAWN'));
ALTER TABLE app_user ADD COLUMN suspended_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN withdrawn_at TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN status_reason VARCHAR(1000);
ALTER TABLE app_user DROP COLUMN active;
CREATE INDEX idx_app_user_account_status ON app_user(account_status,global_role,approval_status);

CREATE TABLE project_member_history (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES app_user(id),
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  left_at TIMESTAMPTZ,
  join_reason VARCHAR(100),
  leave_reason VARCHAR(100),
  actor_id BIGINT REFERENCES app_user(id)
);
INSERT INTO project_member_history(project_id,user_id,joined_at,join_reason)
SELECT project_id,user_id,created_at,'MIGRATED_CURRENT_MEMBER' FROM project_member;
CREATE INDEX idx_project_member_history_user ON project_member_history(user_id,joined_at DESC);
CREATE INDEX idx_project_member_history_project ON project_member_history(project_id,joined_at DESC);

ALTER TABLE todo ADD COLUMN assignment_status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE todo ADD CONSTRAINT ck_todo_assignment_status CHECK (assignment_status IN ('ACTIVE','REASSIGNMENT_REQUIRED'));
ALTER TABLE todo ADD COLUMN normalized_title VARCHAR(600);
ALTER TABLE todo ADD COLUMN possible_duplicate_of_id BIGINT REFERENCES todo(id);
ALTER TABLE todo ADD COLUMN duplicate_reason VARCHAR(100);
UPDATE todo SET normalized_title=lower(regexp_replace(trim(title),'[^[:alnum:]가-힣]+','','g')) WHERE normalized_title IS NULL;
CREATE INDEX idx_todo_normalized_title ON todo(project_id,normalized_title,review_status);
CREATE INDEX idx_todo_assignment_status ON todo(project_id,assignment_status,task_status);

CREATE TABLE todo_reassignment (
  id BIGSERIAL PRIMARY KEY,
  todo_id BIGINT NOT NULL REFERENCES todo(id) ON DELETE CASCADE,
  project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
  former_assignee_id BIGINT REFERENCES app_user(id),
  reason VARCHAR(40) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','RESOLVED')),
  created_by BIGINT REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_by BIGINT REFERENCES app_user(id),
  resolved_at TIMESTAMPTZ,
  new_assignee_id BIGINT REFERENCES app_user(id)
);
CREATE INDEX idx_todo_reassignment_project ON todo_reassignment(project_id,status,created_at DESC);
CREATE INDEX idx_todo_reassignment_todo ON todo_reassignment(todo_id,status);

-- Repair legacy rows: an inactive account must not retain live project access or executable unfinished work.
INSERT INTO todo_reassignment(todo_id,project_id,former_assignee_id,reason)
SELECT t.id,t.project_id,t.assignee_id,'MIGRATED_INACTIVE_ACCOUNT'
FROM todo t JOIN app_user u ON u.id=t.assignee_id
WHERE u.account_status<>'ACTIVE' AND t.review_status='CONFIRMED' AND t.task_status<>'DONE'
  AND NOT EXISTS (SELECT 1 FROM todo_reassignment r WHERE r.todo_id=t.id AND r.status='PENDING');
UPDATE todo t SET assignment_status='REASSIGNMENT_REQUIRED',updated_at=CURRENT_TIMESTAMP
FROM app_user u
WHERE t.assignee_id=u.id AND u.account_status<>'ACTIVE' AND t.review_status='CONFIRMED' AND t.task_status<>'DONE';
UPDATE project_member_history h SET left_at=CURRENT_TIMESTAMP,leave_reason='MIGRATED_INACTIVE_ACCOUNT'
WHERE h.left_at IS NULL AND EXISTS (SELECT 1 FROM app_user u WHERE u.id=h.user_id AND u.account_status<>'ACTIVE');
DELETE FROM project_member pm USING app_user u WHERE pm.user_id=u.id AND u.account_status<>'ACTIVE';
