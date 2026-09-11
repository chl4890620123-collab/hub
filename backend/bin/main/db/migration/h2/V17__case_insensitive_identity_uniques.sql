-- H2 mirror: identity uniqueness must match normalized login/email values.
DROP INDEX IF EXISTS uq_app_user_login_id;
CREATE UNIQUE INDEX uq_app_user_login_id_ci ON app_user (LOWER(login_id));
CREATE UNIQUE INDEX uq_app_user_email_ci ON app_user (LOWER(email));