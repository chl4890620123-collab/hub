-- Bind each auto-sync scope to the account that originally imported it.
-- Existing rows stay unbound on purpose: guessing an owner from today's connector accounts could
-- replay a private repository/channel with the wrong teammate's credential. One manual re-import
-- safely binds the scope again.
ALTER TABLE connector_sync_state ADD COLUMN owner_user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL;
CREATE INDEX idx_connector_sync_owner ON connector_sync_state(owner_user_id);
