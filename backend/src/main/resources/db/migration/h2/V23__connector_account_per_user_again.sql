-- Reconsidered: connector credentials go back to being personal (one per user), because each person's
-- own Google/Slack/GitHub/Notion account can see different files/channels/repos - a single project-wide
-- shared credential could not reach everything a teammate personally has access to. What imported data
-- combines into is still the project's shared document pool (external_item/document), unaffected by this.
-- The (project_id, connector_type) uniqueness added when credentials were briefly project-scoped no
-- longer holds: several members may now each link their own account for the same project.
ALTER TABLE connector_account DROP CONSTRAINT IF EXISTS uq_connector_account_project_type;
