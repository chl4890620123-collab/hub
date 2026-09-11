-- v2.14 local parity: H2 keeps portable LIKE lexical retrieval but receives the same active-source filter index.
CREATE INDEX idx_document_search_active
  ON document(project_id,archived,source_deleted,source_type);
