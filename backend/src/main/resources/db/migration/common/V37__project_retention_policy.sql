-- Project-specific retention policy. Protected documents are exempt from archived-content cleanup.
ALTER TABLE project ADD COLUMN retention_months INT NOT NULL DEFAULT 12;
ALTER TABLE document ADD COLUMN retention_protected BOOLEAN NOT NULL DEFAULT FALSE;
