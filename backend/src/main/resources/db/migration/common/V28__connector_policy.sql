-- Company-wide switch for which connector types anyone may link/import from, independent of any one
-- project (linking/importing is already personal-account-scoped per ConnectorController - this is the
-- separate, coarser decision of whether a service is allowed at all). All four ship enabled so existing
-- installs keep today's behavior until an admin turns one off.
CREATE TABLE connector_policy (
  connector_type VARCHAR(30) PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO connector_policy(connector_type,enabled) VALUES
  ('GOOGLE_DRIVE',TRUE),('GITHUB',TRUE),('SLACK',TRUE),('NOTION',TRUE);
