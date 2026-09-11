-- v2.14: production lexical retrieval uses a generated tsvector + GIN index.
-- Document title stays in the document table and is combined as an exact/substring signal at query time.
ALTER TABLE document_chunk
  ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector(
      'simple'::regconfig,
      COALESCE(heading,'') || ' ' || COALESCE(paragraph_ref,'') || ' ' || content
    )
  ) STORED;

CREATE INDEX idx_chunk_search_fts ON document_chunk USING GIN(search_vector);

CREATE INDEX idx_document_search_active
  ON document(project_id,archived,source_deleted,source_type);
