-- Records when an applicant agreed to the privacy notice shown at signup, so there is an auditable
-- record of consent (not just a client-side checkbox) if that is ever questioned.
ALTER TABLE app_user ADD COLUMN privacy_consent_at TIMESTAMP WITH TIME ZONE;
