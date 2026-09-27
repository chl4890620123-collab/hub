-- Completion submissions can include a reviewable URL in addition to todo file attachments.
ALTER TABLE todo ADD COLUMN completion_url VARCHAR(2000);
