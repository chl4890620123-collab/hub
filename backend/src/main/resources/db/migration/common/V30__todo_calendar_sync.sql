-- Confirming a todo with a due date creates a Google Calendar event (best-effort, only when the
-- assignee's account has linked Google with calendar scope); this stores which event so later
-- status changes update/delete that same event instead of creating duplicates.
ALTER TABLE todo ADD COLUMN IF NOT EXISTS google_calendar_event_id VARCHAR(200);
