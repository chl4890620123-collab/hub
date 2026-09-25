-- Link historical connector snapshots to the normalized document they already represent.
-- This distinguishes legitimate shared/disconnected imports from truly unlinked bookkeeping rows,
-- so retention never removes searchable project data merely because connector_account_id is NULL.
UPDATE external_item
SET imported_document_id = (
  SELECT MIN(d.id)
  FROM document d
  WHERE d.project_id = external_item.project_id
    AND d.source_identifier = external_item.external_id
    AND d.source_type IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
    AND d.source_deleted = FALSE
)
WHERE imported_document_id IS NULL
  AND EXISTS (
    SELECT 1
    FROM document d
    WHERE d.project_id = external_item.project_id
      AND d.source_identifier = external_item.external_id
      AND d.source_type IN ('GITHUB','GOOGLE_DRIVE','SLACK','NOTION')
      AND d.source_deleted = FALSE
  );
