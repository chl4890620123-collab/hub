-- Todo attachments are private messages to the current assignee rather than project-wide files.
-- Existing todo attachments inherit the todo's current assignee. Attachments on an unassigned todo
-- remain sender-only until a future explicit resend; we never guess a recipient.
UPDATE file_attachment fa
SET recipient_id = (
  SELECT t.assignee_id
  FROM todo t
  WHERE t.id = fa.todo_id
)
WHERE fa.todo_id IS NOT NULL
  AND fa.recipient_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM todo t
    WHERE t.id = fa.todo_id
      AND t.assignee_id IS NOT NULL
  );
