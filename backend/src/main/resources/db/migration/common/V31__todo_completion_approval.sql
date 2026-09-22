-- Completion now needs a decision-maker's approval instead of the assignee marking DONE directly;
-- pending_approval is kept as its own flag (not a 5th task_status value) so the existing
-- TODO/IN_PROGRESS/DONE/BLOCKED CHECK constraint never has to be touched.
-- status_note holds whichever short message currently applies to the todo's non-normal state:
-- the assignee's help-request note while BLOCKED, or the decision-maker's reason after a reject.
ALTER TABLE todo ADD COLUMN IF NOT EXISTS pending_approval BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE todo ADD COLUMN IF NOT EXISTS status_note VARCHAR(1000);
