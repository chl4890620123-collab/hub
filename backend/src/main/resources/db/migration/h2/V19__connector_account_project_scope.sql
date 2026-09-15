-- Connector credentials become project-scoped (shared within the project, isolated across projects)
-- instead of per-user. project_id already existed on this table but was never used as the lookup key.
ALTER TABLE connector_account ADD CONSTRAINT uq_connector_account_project_type UNIQUE (project_id, connector_type);
