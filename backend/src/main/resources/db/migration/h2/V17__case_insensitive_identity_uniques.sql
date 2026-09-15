-- H2 mirror: H2 has no expression-index syntax (CREATE INDEX ... (LOWER(col))) the way PostgreSQL
-- does - its parser rejects anything but a plain column list - so case-insensitive uniqueness here
-- is enforced through a generated column instead, which H2 can index like any other.
DROP INDEX IF EXISTS uq_app_user_login_id;
ALTER TABLE app_user ADD COLUMN login_id_ci VARCHAR(40) AS LOWER(login_id);
ALTER TABLE app_user ADD COLUMN email_ci VARCHAR(255) AS LOWER(email);
CREATE UNIQUE INDEX uq_app_user_login_id_ci ON app_user (login_id_ci);
CREATE UNIQUE INDEX uq_app_user_email_ci ON app_user (email_ci);
