-- external_id is already prefixed by connector type in application code.
-- PostgreSQL UNIQUE constraints treat NULL connector_account_id values as distinct,
-- so use an explicit project/external-id unique index for read-only connector imports.
CREATE UNIQUE INDEX IF NOT EXISTS uq_external_item_project_external
    ON external_item(project_id, external_id);
