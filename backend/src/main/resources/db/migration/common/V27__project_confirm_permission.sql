-- Confirming AI-extracted todos (and the "담당자 배정" review screen) used to require global ADMIN,
-- which meant granting it to a team lead also handed them account suspension, role changes, and every
-- other project's data - a permanent global grant for a decision-maker that rotates meeting to meeting.
-- This column lets a project's confirm permission be granted per person, per project, independent of
-- global role. Global ADMIN still passes every check unconditionally (see ProjectAccessService).
ALTER TABLE project_member ADD COLUMN can_confirm_todos BOOLEAN NOT NULL DEFAULT FALSE;
