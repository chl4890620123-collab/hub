-- Tracks when a document was archived so old archived content can be cleared on a retention schedule
-- (document/document_version/document_chunk rows are kept - evidence/decision/todo/change records cite
-- them by id - only the bulky text/embedding columns get cleared once old enough).
ALTER TABLE document ADD COLUMN archived_at TIMESTAMP WITH TIME ZONE;
