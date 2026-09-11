CREATE UNIQUE INDEX IF NOT EXISTS uq_external_item_project_external
    ON external_item(project_id, external_id);
