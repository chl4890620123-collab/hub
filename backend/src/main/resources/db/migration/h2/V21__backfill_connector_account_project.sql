-- V19/V20 moved connector credentials from per-user to per-project lookup, which orphaned any
-- credential linked before that change (project_id was never set). This backfills the unambiguous
-- cases only, so a working connection someone already granted does not silently stop working:
--   - an ADMIN's credential, when the whole system has exactly one project
--   - a MEMBER's credential, when that member belongs to exactly one project
-- Anything still ambiguous after this is left NULL (unusable, same as before this migration) rather
-- than guessed at - the admin can simply reconnect it from the project's connector screen.
UPDATE connector_account
SET project_id = (SELECT id FROM project LIMIT 1)
WHERE project_id IS NULL
  AND EXISTS (SELECT 1 FROM app_user u WHERE u.id = connector_account.user_id AND u.global_role = 'ADMIN')
  AND (SELECT COUNT(*) FROM project) = 1;

UPDATE connector_account
SET project_id = (SELECT pm.project_id FROM project_member pm WHERE pm.user_id = connector_account.user_id)
WHERE project_id IS NULL
  AND (SELECT COUNT(*) FROM project_member pm2 WHERE pm2.user_id = connector_account.user_id) = 1;
