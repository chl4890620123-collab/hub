-- Identity uniqueness must match the normalized login/email values used by authentication.
DROP INDEX IF EXISTS uq_app_user_login_id;
CREATE UNIQUE INDEX uq_app_user_login_id_ci ON app_user (lower(login_id));
CREATE UNIQUE INDEX uq_app_user_email_ci ON app_user (lower(email));