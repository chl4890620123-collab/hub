-- Admin-registered words/values (names, case codes, client names, ...) that regex patterns cannot
-- guess. Whenever one of these appears in a meeting STT transcript, it is redacted the same way as
-- the built-in resident-number/card/phone/email patterns, before the transcript is stored or indexed.
CREATE TABLE sensitive_term (
  id BIGSERIAL PRIMARY KEY,
  term VARCHAR(500) NOT NULL,
  created_by BIGINT NOT NULL REFERENCES app_user(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_sensitive_term_value ON sensitive_term(term);
